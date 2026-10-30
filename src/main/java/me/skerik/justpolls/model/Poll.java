package me.skerik.justpolls.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * A poll as held in memory. The immutable part is what the creator chose; status, times and the vote map change
 * while the poll lives. Votes are kept as {@code voter hash -> option index}: the plugin never needs to know
 * which player voted for what, only whether a given player has voted (see {@code Voters}).
 */
public final class Poll {

    public final long id;
    public final String title;
    public final String description;
    public final List<String> options;
    public final long createdAt;
    public final String createdBy;
    public final boolean discord;
    public final boolean autoOpen;
    public final long openDelayMs;
    public final String permission;
    public final @Nullable String configKey;
    public final long repeatMs;

    private volatile PollStatus status;
    private volatile long startAt;
    private volatile long endAt;
    private volatile boolean webhookPending;
    private volatile Map<String, Integer> votes = new ConcurrentHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();

    public Poll(long id, String title, String description, List<String> options, PollStatus status, long startAt,
                long endAt, long createdAt, String createdBy, boolean discord, boolean autoOpen, long openDelayMs,
                String permission, @Nullable String configKey, long repeatMs, boolean webhookPending) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.options = List.copyOf(options);
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.discord = discord;
        this.autoOpen = autoOpen;
        this.openDelayMs = openDelayMs;
        this.permission = permission;
        this.configKey = configKey;
        this.repeatMs = repeatMs;
        this.webhookPending = webhookPending;
    }

    public PollStatus status() {
        return status;
    }

    public void status(PollStatus status) {
        this.status = status;
    }

    public long startAt() {
        return startAt;
    }

    public void startAt(long startAt) {
        this.startAt = startAt;
    }

    public long endAt() {
        return endAt;
    }

    public void endAt(long endAt) {
        this.endAt = endAt;
    }

    public boolean webhookPending() {
        return webhookPending;
    }

    public void webhookPending(boolean webhookPending) {
        this.webhookPending = webhookPending;
    }

    public boolean isActive() {
        return status == PollStatus.ACTIVE;
    }

    /** Live vote map (hash to option). Writers use {@link #replaceVotes}, voters add through {@link #recordVote}. */
    public Map<String, Integer> votes() {
        return votes;
    }

    public void replaceVotes(Map<String, Integer> fresh) {
        this.votes = new ConcurrentHashMap<>(fresh);
    }

    public void recordVote(String voterHash, int option) {
        votes.put(voterHash, option);
    }

    /** Current counts per option, computed from the vote map. */
    public int[] counts() {
        int[] counts = new int[options.size()];
        for (int option : votes.values()) {
            if (option >= 0 && option < counts.length) counts[option]++;
        }
        return counts;
    }

    /** Only one lifecycle transition (start, finish) may run at a time; returns false if one is already running. */
    public boolean claim() {
        return busy.compareAndSet(false, true);
    }

    public void release() {
        busy.set(false);
    }
}
