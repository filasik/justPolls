package me.skerik.justpolls.gui;

import java.util.List;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.service.PollService;
import me.skerik.justpolls.util.Durations;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Reacts to poll lifecycle events: chat announcements, the automatic menu, the Discord messages.
 * Events arrive on database / scheduler threads; anything that touches players hops onto their own thread.
 */
public final class PollAnnouncer implements PollService.Events {

    private final Context ctx;

    public PollAnnouncer(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void started(Poll poll) {
        ctx.autoOpener.pollStarted(poll);
        ctx.discord.announceStart(poll);
        if (!ctx.settings().announce().chat()) return;
        Messages m = ctx.messages();
        List<Component> lines = m.list("announce.started", Messages.text("title", poll.title),
                Messages.text("time", Durations.format(poll.endAt() - poll.startAt())),
                Messages.arg("id", Long.toString(poll.id)), Messages.arg("label", ctx.plugin.commandLabel()));
        Sound sound = sound();
        ctx.scheduler.runGlobal(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ctx.scheduler.runForEntity(p, () -> {
                    if (!ctx.controller.canVote(p, poll)) return;
                    lines.forEach(p::sendMessage);
                    if (sound != null) p.playSound(sound);
                }, null);
            }
        });
    }

    @Override
    public void ended(PollResult r) {
        Poll poll = r.poll();
        ctx.autoOpener.forgetPoll(poll.id);
        if (ctx.settings().announce().chat()) {
            List<Component> lines = chatSummary(r);
            ctx.scheduler.runGlobal(() -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ctx.scheduler.runForEntity(p, () -> {
                        if (ctx.controller.canVote(p, poll) || p.hasPermission("justpolls.admin")) lines.forEach(p::sendMessage);
                    }, null);
                }
            });
        }
        if (poll.discord && ctx.discord.enabled()) deliverResults(r);
    }

    /** Sends the results to Discord and records the outcome; a failure stays pending for a retry at the next start. */
    public void deliverResults(PollResult r) {
        ctx.discord.sendResults(r).thenAccept(ok -> {
            if (ok) {
                ctx.polls.markWebhookSent(r.poll());
                ctx.log.info("Results of poll #" + r.poll().id + " sent to Discord");
            } else {
                ctx.log.warning("Could not send the results of poll #" + r.poll().id
                        + " to Discord; they stay queued (retry: /justpolls webhook " + r.poll().id + ")");
            }
        });
    }

    @Override
    public void cancelled(Poll poll) {
        ctx.autoOpener.forgetPoll(poll.id);
    }

    private List<Component> chatSummary(PollResult r) {
        Messages m = ctx.messages();
        List<Component> out = new java.util.ArrayList<>(m.list("announce.ended", Messages.text("title", r.poll().title)));
        List<Integer> winners = r.winners();
        String percent = winners.isEmpty() ? "0" : ResultView.percent(r.percent(winners.getFirst()));
        String count = winners.isEmpty() ? "0" : Integer.toString(r.counts()[winners.getFirst()]);
        if (winners.isEmpty()) {
            out.add(m.get("announce.ended-nobody"));
        } else if (winners.size() == 1) {
            out.add(m.get("announce.ended-winner", Messages.text("winner", r.poll().options.get(winners.getFirst())),
                    Messages.text("percent", percent), Messages.text("count", count)));
        } else {
            out.add(m.get("announce.ended-tie", Messages.text("winners",
                    String.join(", ", winners.stream().map(r.poll().options::get).toList())), Messages.text("count", count)));
        }
        for (int i = 0; i < r.counts().length; i++) {
            out.add(m.get("announce.ended-line", new TagResolver[]{Messages.text("option", r.poll().options.get(i)),
                    Messages.text("percent", ResultView.percent(r.percent(i))), Messages.text("count", Integer.toString(r.counts()[i]))}));
        }
        out.add(m.get("announce.ended-end"));
        return out;
    }

    private @Nullable Sound sound() {
        String key = ctx.settings().announce().sound();
        if (key.isEmpty()) return null;
        try {
            return Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f);
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
