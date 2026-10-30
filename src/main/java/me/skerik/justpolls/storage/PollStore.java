package me.skerik.justpolls.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.model.VoteOutcome;
import org.jspecify.annotations.Nullable;

/**
 * All SQL of the plugin. Every method returns a future that completes on the DB executor (the main thread never
 * waits for the database), SQL is parameterized and plain enough for SQLite and MariaDB alike.
 */
public final class PollStore {

    private static final String POLLS = Migrations.table("polls");
    private static final String OPTIONS = Migrations.table("options");
    private static final String VOTES = Migrations.table("votes");
    private static final String META = Migrations.table("meta");
    private static final String COLUMNS = "id, title, description, status, start_at, end_at, created_at, created_by, "
            + "discord, auto_open, open_delay, permission, config_key, repeat_ms, webhook_pending";

    /** Latest poll created from a config.yml entry. */
    public record ConfigPollRef(long id, PollStatus status, long startAt) {
    }

    private final Database db;

    public PollStore(Database db) {
        this.db = db;
    }

    /** The per-install salt of the voter hashes; created on first use. Blocking, call from an async task only. */
    public String salt() throws SQLException {
        return db.call(c -> {
            String existing = metaValue(c, "voter-salt");
            if (existing != null) return existing;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + META + " (k, v) VALUES (?, ?)")) {
                ps.setString(1, "voter-salt");
                ps.setString(2, UUID.randomUUID().toString());
                ps.executeUpdate();
            } catch (SQLException raced) {
                // another server sharing the database inserted it first; read theirs below
            }
            return java.util.Objects.requireNonNull(metaValue(c, "voter-salt"));
        });
    }

    private static @Nullable String metaValue(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM " + META + " WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public CompletableFuture<Poll> create(NewPoll spec, long now, PollStatus status) {
        return db.supply(c -> Database.transaction(c, tx -> {
            long start = Math.max(spec.startAt(), now);
            long end = start + spec.durationMs();
            long id;
            try (PreparedStatement ps = tx.prepareStatement("INSERT INTO " + POLLS
                    + " (title, description, status, start_at, end_at, created_at, created_by, discord, auto_open, "
                    + "open_delay, permission, config_key, repeat_ms, webhook_pending) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, spec.title());
                ps.setString(2, spec.description());
                ps.setString(3, status.name());
                ps.setLong(4, start);
                ps.setLong(5, end);
                ps.setLong(6, now);
                ps.setString(7, spec.createdBy());
                ps.setInt(8, spec.discord() ? 1 : 0);
                ps.setInt(9, spec.autoOpen() ? 1 : 0);
                ps.setLong(10, spec.openDelayMs());
                ps.setString(11, spec.permission());
                ps.setString(12, spec.configKey());
                ps.setLong(13, spec.repeatMs());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement ps = tx.prepareStatement("INSERT INTO " + OPTIONS + " (poll_id, idx, label) VALUES (?,?,?)")) {
                for (int i = 0; i < spec.options().size(); i++) {
                    ps.setLong(1, id);
                    ps.setInt(2, i);
                    ps.setString(3, spec.options().get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return new Poll(id, spec.title(), spec.description(), spec.options(), status, start, end, now,
                    spec.createdBy(), spec.discord(), spec.autoOpen(), spec.openDelayMs(), spec.permission(),
                    spec.configKey(), spec.repeatMs(), false);
        }));
    }

    /** Scheduled and active polls with their votes: the working set of the running plugin. */
    public CompletableFuture<List<Poll>> loadOpen() {
        return db.supply(c -> {
            List<Poll> polls = readPolls(c, "WHERE status IN ('SCHEDULED','ACTIVE') ORDER BY id", null);
            for (Poll p : polls) p.replaceVotes(votes(c, p.id));
            return polls;
        });
    }

    /** Ended polls whose Discord message has not been delivered yet (crash, Discord down). */
    public CompletableFuture<List<Poll>> loadWebhookPending() {
        return db.supply(c -> readPolls(c, "WHERE status = 'ENDED' AND webhook_pending = 1 ORDER BY id", null));
    }

    public CompletableFuture<Optional<Poll>> find(long id) {
        return db.supply(c -> readPolls(c, "WHERE id = ?", id).stream().findFirst());
    }

    /** Newest polls first, any status. */
    public CompletableFuture<List<Poll>> recent(int limit) {
        return db.supply(c -> readPolls(c, "ORDER BY id DESC LIMIT " + Math.max(1, limit), null));
    }

    public CompletableFuture<Optional<ConfigPollRef>> latestByConfigKey(String key) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, status, start_at FROM " + POLLS
                    + " WHERE config_key = ? ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new ConfigPollRef(rs.getLong(1), PollStatus.valueOf(rs.getString(2)), rs.getLong(3)))
                            : Optional.<ConfigPollRef>empty();
                }
            }
        });
    }

    private static List<Poll> readPolls(Connection c, String tail, @Nullable Long id) throws SQLException {
        List<Poll> polls = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM " + POLLS + " " + tail)) {
            if (id != null) ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long pid = rs.getLong("id");
                    polls.add(new Poll(pid, rs.getString("title"), rs.getString("description"), options(c, pid),
                            PollStatus.valueOf(rs.getString("status")), rs.getLong("start_at"), rs.getLong("end_at"),
                            rs.getLong("created_at"), rs.getString("created_by"), rs.getInt("discord") == 1,
                            rs.getInt("auto_open") == 1, rs.getLong("open_delay"), rs.getString("permission"),
                            rs.getString("config_key"), rs.getLong("repeat_ms"), rs.getInt("webhook_pending") == 1));
                }
            }
        }
        return polls;
    }

    private static List<String> options(Connection c, long pollId) throws SQLException {
        List<String> labels = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT label FROM " + OPTIONS + " WHERE poll_id = ? ORDER BY idx")) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) labels.add(rs.getString(1));
            }
        }
        return labels;
    }

    private static Map<String, Integer> votes(Connection c, long pollId) throws SQLException {
        Map<String, Integer> votes = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT voter, option_idx FROM " + VOTES + " WHERE poll_id = ?")) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) votes.put(rs.getString(1), rs.getInt(2));
            }
        }
        return votes;
    }

    /** Authoritative counts straight from the database (used for the final result). */
    public CompletableFuture<int[]> tally(long pollId, int optionCount) {
        return db.supply(c -> tally(c, pollId, optionCount));
    }

    private static int[] tally(Connection c, long pollId, int optionCount) throws SQLException {
        int[] counts = new int[optionCount];
        try (PreparedStatement ps = c.prepareStatement("SELECT option_idx, COUNT(*) FROM " + VOTES
                + " WHERE poll_id = ? GROUP BY option_idx")) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = rs.getInt(1);
                    if (idx >= 0 && idx < optionCount) counts[idx] = rs.getInt(2);
                }
            }
        }
        return counts;
    }

    /**
     * Records a vote if the poll is still active. One vote per voter and poll is enforced by the primary key;
     * with {@code allowChange} a second call moves the vote.
     */
    public CompletableFuture<VoteOutcome> vote(long pollId, String voterHash, int option, int optionCount,
                                               boolean allowChange, long now) {
        return db.supply(c -> Database.transaction(c, tx -> {
            if (option < 0 || option >= optionCount) return VoteOutcome.INVALID_OPTION;
            try (PreparedStatement ps = tx.prepareStatement("SELECT status FROM " + POLLS + " WHERE id = ?")) {
                ps.setLong(1, pollId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !rs.getString(1).equals(PollStatus.ACTIVE.name())) return VoteOutcome.CLOSED;
                }
            }
            Integer previous = null;
            try (PreparedStatement ps = tx.prepareStatement("SELECT option_idx FROM " + VOTES + " WHERE poll_id = ? AND voter = ?")) {
                ps.setLong(1, pollId);
                ps.setString(2, voterHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) previous = rs.getInt(1);
                }
            }
            if (previous != null) {
                if (!allowChange || previous == option) return VoteOutcome.ALREADY_VOTED;
                try (PreparedStatement ps = tx.prepareStatement("UPDATE " + VOTES
                        + " SET option_idx = ?, voted_at = ? WHERE poll_id = ? AND voter = ?")) {
                    ps.setInt(1, option);
                    ps.setLong(2, now);
                    ps.setLong(3, pollId);
                    ps.setString(4, voterHash);
                    ps.executeUpdate();
                }
                return VoteOutcome.CHANGED;
            }
            try (PreparedStatement ps = tx.prepareStatement("INSERT INTO " + VOTES
                    + " (poll_id, voter, option_idx, voted_at) VALUES (?,?,?,?)")) {
                ps.setLong(1, pollId);
                ps.setString(2, voterHash);
                ps.setInt(3, option);
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException duplicate) {
                return VoteOutcome.ALREADY_VOTED; // a concurrent vote of the same player on another server won
            }
            return VoteOutcome.VOTED;
        }));
    }

    /** Moves a poll from {@code from} to {@code to}; false if it was not in {@code from} (someone else was faster). */
    public CompletableFuture<Boolean> transition(long pollId, PollStatus from, PollStatus to, boolean webhookPending) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + POLLS + " SET status = ?, webhook_pending = ? "
                    + "WHERE id = ? AND status = ?")) {
                ps.setString(1, to.name());
                ps.setInt(2, webhookPending ? 1 : 0);
                ps.setLong(3, pollId);
                ps.setString(4, from.name());
                return ps.executeUpdate() == 1;
            }
        });
    }

    /** Cancels a scheduled or active poll. */
    public CompletableFuture<Boolean> cancel(long pollId) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + POLLS + " SET status = 'CANCELLED', webhook_pending = 0 "
                    + "WHERE id = ? AND status IN ('SCHEDULED','ACTIVE')")) {
                ps.setLong(1, pollId);
                return ps.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> setTimes(long pollId, long startAt, long endAt) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + POLLS + " SET start_at = ?, end_at = ? WHERE id = ?")) {
                ps.setLong(1, startAt);
                ps.setLong(2, endAt);
                ps.setLong(3, pollId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> setWebhookPending(long pollId, boolean pending) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + POLLS + " SET webhook_pending = ? WHERE id = ?")) {
                ps.setInt(1, pending ? 1 : 0);
                ps.setLong(2, pollId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> delete(long pollId) {
        return db.supply(c -> Database.transaction(c, tx -> {
            for (String table : List.of(VOTES, OPTIONS)) {
                try (PreparedStatement ps = tx.prepareStatement("DELETE FROM " + table + " WHERE poll_id = ?")) {
                    ps.setLong(1, pollId);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = tx.prepareStatement("DELETE FROM " + POLLS + " WHERE id = ?")) {
                ps.setLong(1, pollId);
                return ps.executeUpdate() == 1;
            }
        }));
    }
}
