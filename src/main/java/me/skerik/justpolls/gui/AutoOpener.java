package me.skerik.justpolls.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.model.Poll;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Decides when the voting menu pops up by itself. State per (player, poll): when it is due and how often it has
 * opened. A poll start, a join and the reminder interval only set or move that due time; one timer
 * ({@link #tick}) does the opening. Opening is rechecked on the player's own thread (permission, already voted,
 * opted out), so a stale entry is harmless.
 */
public final class AutoOpener {

    private static final NamespacedKey OPTED_OUT = new NamespacedKey("justpolls", "auto_open_off");

    private record Key(UUID player, long poll) {
    }

    private static final class State {
        long dueAt;
        int opens;

        State(long dueAt) {
            this.dueAt = dueAt;
        }
    }

    private final Context ctx;
    private final Map<Key, State> states = new ConcurrentHashMap<>();

    public AutoOpener(Context ctx) {
        this.ctx = ctx;
    }

    public static boolean isOptedOut(Player player) {
        return player.getPersistentDataContainer().has(OPTED_OUT, PersistentDataType.BYTE);
    }

    public static void setOptedOut(Player player, boolean optedOut) {
        if (optedOut) player.getPersistentDataContainer().set(OPTED_OUT, PersistentDataType.BYTE, (byte) 1);
        else player.getPersistentDataContainer().remove(OPTED_OUT);
    }

    /** A poll became active: schedule the menu for everybody online. */
    public void pollStarted(Poll poll) {
        Settings.AutoOpen cfg = ctx.settings().autoOpen();
        if (!cfg.enabled() || !poll.autoOpen) return;
        long delay = poll.openDelayMs >= 0 ? poll.openDelayMs : cfg.onStartDelayMs();
        long due = System.currentTimeMillis() + delay;
        for (Player p : Bukkit.getOnlinePlayers()) states.putIfAbsent(new Key(p.getUniqueId(), poll.id), new State(due));
    }

    /** A player joined: schedule the menu for every running poll that waits for them. */
    public void playerJoined(Player player) {
        Settings.AutoOpen cfg = ctx.settings().autoOpen();
        if (!cfg.enabled() || cfg.onJoinDelayMs() <= 0) return;
        long due = System.currentTimeMillis() + cfg.onJoinDelayMs();
        for (Poll poll : ctx.polls.activePolls()) {
            if (poll.autoOpen && !ctx.polls.hasVoted(poll, player.getUniqueId())) {
                states.putIfAbsent(new Key(player.getUniqueId(), poll.id), new State(due));
            }
        }
    }

    public void forgetPlayer(UUID player) {
        states.keySet().removeIf(k -> k.player.equals(player));
    }

    public void forgetPoll(long poll) {
        states.keySet().removeIf(k -> k.poll == poll);
    }

    public void forget(UUID player, long poll) {
        states.remove(new Key(player, poll));
    }

    /** Called every couple of seconds on the global scheduler. */
    public void tick() {
        if (states.isEmpty()) return;
        Settings.AutoOpen cfg = ctx.settings().autoOpen();
        long now = System.currentTimeMillis();
        Map<UUID, List<Long>> due = new HashMap<>();
        states.entrySet().removeIf(e -> {
            Key k = e.getKey();
            Poll poll = ctx.polls.openPoll(k.poll).orElse(null);
            if (poll == null || Bukkit.getPlayer(k.player) == null) return true;
            State s = e.getValue();
            if (!poll.isActive() || s.dueAt > now) return false;
            due.computeIfAbsent(k.player, u -> new ArrayList<>()).add(k.poll);
            s.opens++;
            // reminder: due again later; no reminder or too many opens: park the entry until the poll ends
            s.dueAt = cfg.reminderMs() > 0 && s.opens < cfg.maxOpens() ? now + cfg.reminderMs() : Long.MAX_VALUE;
            return false;
        });
        due.forEach((uuid, polls) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) ctx.scheduler.runForEntity(p, () -> ctx.controller.autoOpen(p, polls), null);
        });
    }
}
