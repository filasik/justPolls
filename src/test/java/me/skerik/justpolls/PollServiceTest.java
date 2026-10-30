package me.skerik.justpolls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.model.VoteOutcome;
import me.skerik.justpolls.service.PollService;
import me.skerik.justpolls.service.PollService.ConfigPoll;
import me.skerik.justpolls.storage.Database;
import me.skerik.justpolls.storage.DbConfig;
import me.skerik.justpolls.storage.Migrations;
import me.skerik.justpolls.storage.PollStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The whole lifecycle against a real SQLite database and a clock the test moves by hand. */
class PollServiceTest {

    @TempDir java.nio.file.Path dir;

    private Database db;
    private PollService service;
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final List<String> events = new ArrayList<>();
    private final List<PollResult> ended = new ArrayList<>();

    private final PollService.Events listener = new PollService.Events() {
        @Override
        public void started(Poll poll) {
            events.add("started:" + poll.id);
        }

        @Override
        public void ended(PollResult result) {
            events.add("ended:" + result.poll().id);
            ended.add(result);
        }

        @Override
        public void cancelled(Poll poll) {
            events.add("cancelled:" + poll.id);
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(DbConfig.sqlite(dir.resolve("polls.db"), 4));
        Migrations.migrate(db);
        service = new PollService(new PollStore(db), now::get, listener, Logger.getLogger("test"));
        service.init();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static NewPoll spec(long durationMs) {
        return new NewPoll("Best colour?", "pick one", List.of("Red", "Green", "Blue"), durationMs, 0,
                true, true, -1, "", null, 0, "tester");
    }

    private Poll create(long durationMs) {
        return service.create(spec(durationMs)).join();
    }

    /** tick() works asynchronously; wait until the database executor has drained. */
    private void tickAndWait() throws Exception {
        service.tick();
        db.supply(c -> null).join();
        Thread.sleep(50);
        db.supply(c -> null).join();
    }

    @Test
    void oneVotePerPlayerAndTheFinalTallyIsRight() throws Exception {
        Poll poll = create(60_000);
        assertTrue(poll.isActive());
        assertEquals(List.of("started:" + poll.id), events);

        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carol = UUID.randomUUID();
        assertEquals(VoteOutcome.VOTED, service.vote(poll, alice, 0, false).join());
        assertEquals(VoteOutcome.VOTED, service.vote(poll, bob, 0, false).join());
        assertEquals(VoteOutcome.VOTED, service.vote(poll, carol, 2, false).join());
        assertEquals(VoteOutcome.ALREADY_VOTED, service.vote(poll, alice, 1, false).join(), "no second vote, not even another option");
        assertEquals(VoteOutcome.ALREADY_VOTED, service.vote(poll, alice, 0, false).join());
        assertEquals(VoteOutcome.INVALID_OPTION, service.vote(poll, UUID.randomUUID(), 7, false).join());
        assertTrue(service.hasVoted(poll, alice));
        assertFalse(service.hasVoted(poll, UUID.randomUUID()));
        assertEquals(0, service.votedOption(poll, alice));
        assertArrayEquals(new int[]{2, 0, 1}, poll.counts());

        now.addAndGet(60_001);
        tickAndWait();
        assertEquals(1, ended.size());
        assertArrayEquals(new int[]{2, 0, 1}, ended.getFirst().counts());
        assertEquals(List.of(0), ended.getFirst().winners());
        assertEquals(3, ended.getFirst().total());
        assertTrue(service.openPoll(poll.id).isEmpty());
        assertEquals(PollStatus.ENDED, service.find(poll.id).join().orElseThrow().status());
        assertEquals(VoteOutcome.CLOSED, service.vote(poll, UUID.randomUUID(), 1, false).join());

        // ending twice is impossible
        tickAndWait();
        assertEquals(1, ended.size());
    }

    @Test
    void votesAreStoredPseudonymously() throws Exception {
        Poll poll = create(60_000);
        UUID alice = UUID.randomUUID();
        service.vote(poll, alice, 1, false).join();
        String stored = db.call(c -> {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT voter FROM " + Migrations.table("votes"))) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertFalse(stored.contains(alice.toString()));
        assertEquals(64, stored.length());
    }

    @Test
    void allowChangeMovesTheVoteButStillCountsOnce() {
        Poll poll = create(60_000);
        UUID alice = UUID.randomUUID();
        assertEquals(VoteOutcome.VOTED, service.vote(poll, alice, 0, true).join());
        assertEquals(VoteOutcome.ALREADY_VOTED, service.vote(poll, alice, 0, true).join(), "same option again is not a change");
        assertEquals(VoteOutcome.CHANGED, service.vote(poll, alice, 2, true).join());
        assertArrayEquals(new int[]{0, 0, 1}, poll.counts());
        assertArrayEquals(new int[]{0, 0, 1}, service.result(poll).join().counts());
    }

    @Test
    void concurrentVotesOfOnePlayerCountOnce() throws Exception {
        Poll poll = create(60_000);
        UUID alice = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<VoteOutcome>> results = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int option = i % 3;
            results.add(pool.submit(() -> service.vote(poll, alice, option, false).join()));
        }
        long accepted = 0;
        for (Future<VoteOutcome> f : results) if (f.get() == VoteOutcome.VOTED) accepted++;
        pool.shutdown();
        assertEquals(1, accepted);
        assertEquals(1, service.result(poll).join().total());
    }

    @Test
    void manyDifferentVotersAllCount() {
        Poll poll = create(60_000);
        for (int i = 0; i < 200; i++) service.vote(poll, UUID.randomUUID(), i % 2, false).join();
        assertArrayEquals(new int[]{100, 100, 0}, poll.counts());
        assertArrayEquals(new int[]{100, 100, 0}, service.result(poll).join().counts());
        assertEquals(List.of(0, 1), service.result(poll).join().winners(), "a tie has several winners");
    }

    @Test
    void scheduledPollStartsOnTime() throws Exception {
        NewPoll later = new NewPoll("Later", "", List.of("a", "b"), 60_000, now.get() + 10_000, false, true, -1, "", null, 0, "tester");
        Poll poll = service.create(later).join();
        assertEquals(PollStatus.SCHEDULED, poll.status());
        assertTrue(events.isEmpty());
        assertEquals(VoteOutcome.CLOSED, service.vote(poll, UUID.randomUUID(), 0, false).join());
        now.addAndGet(10_001);
        tickAndWait();
        assertEquals(PollStatus.ACTIVE, poll.status());
        assertEquals(List.of("started:" + poll.id), events);
        assertEquals(VoteOutcome.VOTED, service.vote(poll, UUID.randomUUID(), 0, false).join());
    }

    @Test
    void endNowCancelDeleteExtend() throws Exception {
        Poll a = create(60_000);
        assertTrue(service.extend(a.id, 30_000).join());
        assertEquals(now.get() + 90_000, a.endAt());
        assertFalse(service.extend(a.id, -1_000_000).join(), "cannot move the end into the past");
        assertTrue(service.extend(a.id, -30_000).join());

        service.vote(a, UUID.randomUUID(), 1, false).join();
        assertTrue(service.endNow(a.id).join());
        assertEquals(1, ended.size());
        assertArrayEquals(new int[]{0, 1, 0}, ended.getFirst().counts());
        assertFalse(service.endNow(a.id).join(), "already ended");

        Poll b = create(60_000);
        assertTrue(service.cancel(b.id).join());
        assertEquals(PollStatus.CANCELLED, service.find(b.id).join().orElseThrow().status());
        assertTrue(events.contains("cancelled:" + b.id));
        assertEquals(1, ended.size(), "a cancelled poll publishes no results");

        assertTrue(service.delete(a.id).join());
        assertTrue(service.find(a.id).join().isEmpty());
        assertFalse(service.delete(a.id).join());
    }

    @Test
    void openPollsSurviveARestart() throws Exception {
        Poll poll = create(600_000);
        UUID alice = UUID.randomUUID();
        service.vote(poll, alice, 2, false).join();

        PollService restarted = new PollService(new PollStore(db), now::get, listener, Logger.getLogger("test"));
        restarted.init();
        Poll again = restarted.openPoll(poll.id).orElseThrow();
        assertEquals(List.of("Red", "Green", "Blue"), again.options);
        assertEquals("pick one", again.description);
        assertTrue(restarted.hasVoted(again, alice), "the voter hash survives because the salt is stored");
        assertArrayEquals(new int[]{0, 0, 1}, again.counts());
        assertEquals(VoteOutcome.ALREADY_VOTED, restarted.vote(again, alice, 0, false).join());
    }

    @Test
    void pollThatExpiredWhileOfflineEndsOnTheFirstTick() throws Exception {
        Poll poll = create(60_000);
        service.vote(poll, UUID.randomUUID(), 0, false).join();
        now.addAndGet(5_000_000);
        PollService restarted = new PollService(new PollStore(db), now::get, listener, Logger.getLogger("test"));
        restarted.init();
        restarted.tick();
        Thread.sleep(150);
        assertEquals(1, ended.size());
        assertEquals(poll.id, ended.getFirst().poll().id);
    }

    @Test
    void webhookFlagStaysPendingUntilDelivered() throws Exception {
        Poll poll = create(60_000);
        service.endNow(poll.id).join();
        List<PollResult> pending = service.pendingWebhooks().join();
        assertEquals(1, pending.size());
        service.markWebhookSent(pending.getFirst().poll()).join();
        assertTrue(service.pendingWebhooks().join().isEmpty());

        NewPoll quiet = new NewPoll("No discord", "", List.of("a", "b"), 60_000, 0, false, true, -1, "", null, 0, "t");
        Poll q = service.create(quiet).join();
        service.endNow(q.id).join();
        assertTrue(service.pendingWebhooks().join().isEmpty(), "polls without the Discord switch are never queued");
    }

    @Test
    void configPollsAreCreatedOnceAndRepeatOnSchedule() throws Exception {
        NewPoll template = new NewPoll("Weekly", "", List.of("a", "b"), 60_000, 0, false, true, -1, "", "weekly", 3_600_000, "config");
        ConfigPoll cp = new ConfigPoll("weekly", template);

        service.applyConfigPolls(List.of(cp)).join();
        assertEquals(1, service.openCount());
        service.applyConfigPolls(List.of(cp)).join();
        assertEquals(1, service.openCount(), "an existing config poll is never duplicated");

        Poll first = service.openPolls().getFirst();
        now.addAndGet(60_001);
        tickAndWait();
        assertEquals(0, service.openCount());
        service.applyConfigPolls(List.of(cp)).join();
        assertEquals(0, service.openCount(), "repeat interval has not passed yet");

        now.set(first.startAt() + 3_600_000 + 1);
        service.applyConfigPolls(List.of(cp)).join();
        assertEquals(1, service.openCount());
        assertEquals(now.get(), service.openPolls().getFirst().startAt(), "a poll that is overdue starts now");

        // without repeat, nothing comes back once the poll has ended
        NewPoll once = new NewPoll("Once", "", List.of("a", "b"), 60_000, 0, false, true, -1, "", "once", 0, "config");
        service.applyConfigPolls(List.of(new ConfigPoll("once", once))).join();
        Poll o = service.openPolls().stream().filter(p -> "once".equals(p.configKey)).findFirst().orElseThrow();
        assertNotNull(o);
        service.endNow(o.id).join();
        service.applyConfigPolls(List.of(new ConfigPoll("once", once))).join();
        assertTrue(service.openPolls().stream().noneMatch(p -> "once".equals(p.configKey)));
    }
}
