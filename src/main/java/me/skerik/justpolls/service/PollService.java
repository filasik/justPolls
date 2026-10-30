package me.skerik.justpolls.service;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.model.VoteOutcome;
import me.skerik.justpolls.storage.PollStore;
import me.skerik.justpolls.util.Voters;
import org.jspecify.annotations.Nullable;

/**
 * The poll lifecycle, free of Bukkit so it can be unit-tested: create, vote, start/end on schedule, cancel,
 * extend, delete. Scheduled and active polls live in memory (the working set); everything else is read from the
 * database when needed. The database is the authority: a transition only happens for the caller whose conditional
 * UPDATE succeeded, so two servers sharing a database never end a poll (or send its Discord message) twice.
 */
public final class PollService {

    /** What the rest of the plugin reacts to. Called from DB / scheduler threads: hop to the right thread yourself. */
    public interface Events {
        void started(Poll poll);

        void ended(PollResult result);

        void cancelled(Poll poll);
    }

    private final PollStore store;
    private final LongSupplier clock;
    private final Events events;
    private final Logger log;
    private final Map<Long, Poll> open = new ConcurrentHashMap<>();
    private volatile String salt = "";

    public PollService(PollStore store, LongSupplier clock, Events events, Logger log) {
        this.store = store;
        this.clock = clock;
        this.events = events;
        this.log = log;
    }

    /** Loads the salt and the working set. Blocking: startup (async thread) and tests only. */
    public void init() throws SQLException {
        salt = store.salt();
        refresh().join();
    }

    /**
     * Re-reads scheduled and active polls (and their votes) from the database. Catches changes made by another
     * server that shares the database; also the way polls get into memory at startup.
     */
    public CompletableFuture<Void> refresh() {
        return store.loadOpen().thenAccept(fresh -> {
            Set<Long> seen = new HashSet<>();
            for (Poll p : fresh) {
                seen.add(p.id);
                Poll cached = open.get(p.id);
                if (cached == null) {
                    open.put(p.id, p);
                } else {
                    cached.status(p.status());
                    cached.startAt(p.startAt());
                    cached.endAt(p.endAt());
                    cached.replaceVotes(p.votes());
                }
            }
            open.keySet().removeIf(id -> !seen.contains(id));
        });
    }

    // --- queries ---------------------------------------------------------------------------------------------

    public List<Poll> activePolls() {
        return open.values().stream().filter(Poll::isActive).sorted(Comparator.comparingLong(p -> p.id)).toList();
    }

    public List<Poll> openPolls() {
        return open.values().stream().sorted(Comparator.comparingLong(p -> p.id)).toList();
    }

    public int openCount() {
        return open.size();
    }

    public Optional<Poll> openPoll(long id) {
        return Optional.ofNullable(open.get(id));
    }

    /** Any poll by id: from memory if open, else from the database. */
    public CompletableFuture<Optional<Poll>> find(long id) {
        Poll cached = open.get(id);
        return cached != null ? CompletableFuture.completedFuture(Optional.of(cached)) : store.find(id);
    }

    public CompletableFuture<List<Poll>> recent(int limit) {
        return store.recent(limit);
    }

    /** Counts of any poll: live from memory if open, final from the database otherwise. */
    public CompletableFuture<PollResult> result(Poll poll) {
        if (open.containsKey(poll.id)) {
            return CompletableFuture.completedFuture(new PollResult(poll, poll.counts()));
        }
        return store.tally(poll.id, poll.options.size()).thenApply(c -> new PollResult(poll, c));
    }

    public boolean hasVoted(Poll poll, UUID player) {
        return poll.votes().containsKey(Voters.hash(salt, poll.id, player));
    }

    /** The option the player chose, or -1. Only for showing a player their own vote. */
    public int votedOption(Poll poll, UUID player) {
        Integer option = poll.votes().get(Voters.hash(salt, poll.id, player));
        return option == null ? -1 : option;
    }

    // --- changes ---------------------------------------------------------------------------------------------

    public CompletableFuture<Poll> create(NewPoll spec) {
        long now = clock.getAsLong();
        PollStatus status = spec.startAt() > now ? PollStatus.SCHEDULED : PollStatus.ACTIVE;
        return store.create(spec, now, status).thenApply(poll -> {
            open.put(poll.id, poll);
            if (poll.isActive()) events.started(poll);
            return poll;
        });
    }

    public CompletableFuture<VoteOutcome> vote(Poll poll, UUID player, int option, boolean allowChange) {
        if (!poll.isActive() || poll.endAt() <= clock.getAsLong()) {
            return CompletableFuture.completedFuture(VoteOutcome.CLOSED);
        }
        String hash = Voters.hash(salt, poll.id, player);
        return store.vote(poll.id, hash, option, poll.options.size(), allowChange, clock.getAsLong()).thenApply(outcome -> {
            if (outcome == VoteOutcome.VOTED || outcome == VoteOutcome.CHANGED) poll.recordVote(hash, option);
            return outcome;
        });
    }

    /** Starts due polls and ends expired ones. Cheap when nothing is due; call it every second or so. */
    public void tick() {
        long now = clock.getAsLong();
        for (Poll p : open.values()) {
            if (p.status() == PollStatus.SCHEDULED && p.startAt() <= now && p.claim()) {
                activate(p).whenComplete((v, t) -> p.release());
            } else if (p.isActive() && p.endAt() <= now && p.claim()) {
                finish(p).whenComplete((v, t) -> p.release());
            }
        }
    }

    private CompletableFuture<Void> activate(Poll p) {
        return store.transition(p.id, PollStatus.SCHEDULED, PollStatus.ACTIVE, false).handle((changed, t) -> {
            if (t != null) {
                log.log(Level.WARNING, "Cannot start poll #" + p.id, t);
            } else if (changed) {
                p.status(PollStatus.ACTIVE);
                events.started(p);
            }
            return null;
        });
    }

    private CompletableFuture<Void> finish(Poll p) {
        return store.transition(p.id, PollStatus.ACTIVE, PollStatus.ENDED, p.discord)
                .thenCompose(changed -> {
                    if (!changed) return CompletableFuture.completedFuture((int[]) null);
                    return store.tally(p.id, p.options.size());
                })
                .handle((counts, t) -> {
                    if (t != null) {
                        log.log(Level.WARNING, "Cannot end poll #" + p.id, t);
                    } else {
                        open.remove(p.id);
                        if (counts != null) {
                            p.status(PollStatus.ENDED);
                            p.webhookPending(p.discord);
                            events.ended(new PollResult(p, counts));
                        } // else: another server ended or cancelled it; nothing to announce here
                    }
                    return null;
                });
    }

    /** Ends an active poll right now. False if there is no such active poll. */
    public CompletableFuture<Boolean> endNow(long id) {
        Poll p = open.get(id);
        if (p == null || !p.isActive() || !p.claim()) return CompletableFuture.completedFuture(false);
        long now = clock.getAsLong();
        p.endAt(Math.min(p.endAt(), now));
        return store.setTimes(p.id, p.startAt(), p.endAt()).thenCompose(v -> finish(p)).handle((v, t) -> {
            p.release();
            return t == null;
        });
    }

    public CompletableFuture<Boolean> cancel(long id) {
        Poll p = open.get(id);
        if (p == null) return CompletableFuture.completedFuture(false);
        return store.cancel(id).thenApply(changed -> {
            open.remove(id);
            if (changed) {
                p.status(PollStatus.CANCELLED);
                events.cancelled(p);
            }
            return changed;
        });
    }

    public CompletableFuture<Boolean> delete(long id) {
        Poll cached = open.remove(id);
        return store.delete(id).thenApply(deleted -> {
            if (deleted && cached != null) cached.status(PollStatus.CANCELLED); // makes pending votes see "closed"
            return deleted;
        });
    }

    /** Moves the end of a scheduled or active poll by {@code deltaMs}. False if it is not open or would end in the past. */
    public CompletableFuture<Boolean> extend(long id, long deltaMs) {
        Poll p = open.get(id);
        if (p == null) return CompletableFuture.completedFuture(false);
        long newEnd = p.endAt() + deltaMs;
        if (newEnd <= clock.getAsLong()) return CompletableFuture.completedFuture(false);
        return store.setTimes(id, p.startAt(), newEnd).thenApply(v -> {
            p.endAt(newEnd);
            return true;
        });
    }

    public CompletableFuture<Void> markWebhookSent(Poll poll) {
        poll.webhookPending(false);
        return store.setWebhookPending(poll.id, false);
    }

    public CompletableFuture<Void> requestWebhook(Poll poll) {
        poll.webhookPending(true);
        return store.setWebhookPending(poll.id, true);
    }

    /** Ended polls whose Discord message is still owed, each with its final counts. */
    public CompletableFuture<List<PollResult>> pendingWebhooks() {
        return store.loadWebhookPending().thenCompose(polls -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<PollResult>[] all = polls.stream().map(this::result).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(all).thenApply(v ->
                    java.util.Arrays.stream(all).map(CompletableFuture::join).toList());
        });
    }

    // --- polls defined in config.yml -----------------------------------------------------------------------------

    /** A poll template from the {@code polls:} section of config.yml. */
    public record ConfigPoll(String key, NewPoll template) {
    }

    /**
     * Creates the config polls that are due: never created before, or (with a repeat interval) the previous one
     * ended and the interval since its start has passed. A poll that already exists is left alone, so editing the
     * config never resets running votes.
     */
    public CompletableFuture<Void> applyConfigPolls(List<ConfigPoll> configured) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ConfigPoll cp : configured) {
            chain = chain.thenCompose(v -> applyConfigPoll(cp)).exceptionally(t -> {
                log.log(Level.WARNING, "Cannot apply polls." + cp.key(), t);
                return null;
            });
        }
        return chain;
    }

    private CompletableFuture<Void> applyConfigPoll(ConfigPoll cp) {
        boolean running = open.values().stream().anyMatch(p -> cp.key().equals(p.configKey));
        if (running) return CompletableFuture.completedFuture(null);
        return store.latestByConfigKey(cp.key()).thenCompose(latest -> {
            long now = clock.getAsLong();
            NewPoll t = cp.template();
            if (latest.isEmpty()) return create(t).thenApply(p -> (Void) null);
            if (t.repeatMs() <= 0) return CompletableFuture.<Void>completedFuture(null);
            long due = latest.get().startAt() + t.repeatMs();
            if (due > now) return CompletableFuture.<Void>completedFuture(null);
            NewPoll next = new NewPoll(t.title(), t.description(), t.options(), t.durationMs(), due,
                    t.discord(), t.autoOpen(), t.openDelayMs(), t.permission(), t.configKey(), t.repeatMs(), t.createdBy());
            return create(next).thenApply(p -> (Void) null);
        });
    }

    /** For tests and diagnostics. */
    public @Nullable Poll cached(long id) {
        return open.get(id);
    }
}
