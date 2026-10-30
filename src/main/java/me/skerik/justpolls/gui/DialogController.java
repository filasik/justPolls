package me.skerik.justpolls.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.model.VoteOutcome;
import me.skerik.justpolls.util.Durations;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * What happens when a player opens a menu or clicks in one, and the staff operations that menus and commands share.
 * Every click is treated as untrusted input: permissions and the state of the poll are checked again here, on the
 * player's own thread; slow work (database, Discord) is asynchronous and comes back through the scheduler.
 */
public final class DialogController {

    private final Context ctx;

    public DialogController(Context ctx) {
        this.ctx = ctx;
    }

    private Messages m() {
        return ctx.messages();
    }

    private Settings s() {
        return ctx.settings();
    }

    // --- permissions -----------------------------------------------------------------------------------------

    public boolean canVote(Player p, Poll poll) {
        return p.hasPermission("justpolls.vote") && (poll.permission.isEmpty() || p.hasPermission(poll.permission));
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("justpolls.admin");
    }

    private boolean deny(Player p, boolean allowed) {
        if (!allowed) p.sendMessage(m().get("error.no-permission"));
        return !allowed;
    }

    private void show(Player p, io.papermc.paper.dialog.Dialog dialog) {
        ctx.scheduler.runForEntity(p, () -> p.showDialog(dialog), null);
    }

    private void onPlayer(Player p, Runnable task) {
        ctx.scheduler.runForEntity(p, task, null);
    }

    private <T> void fail(Player p, Throwable t, String what) {
        ctx.log.log(Level.WARNING, what, t);
        onPlayer(p, () -> p.sendMessage(m().get("error.generic")));
    }

    // --- opening menus ---------------------------------------------------------------------------------------

    /** /justpolls with no arguments: the one running poll, or a list when there are several. */
    public void openDefault(Player p) {
        List<Poll> polls = ctx.polls.activePolls().stream().filter(poll -> canVote(p, poll)).toList();
        if (polls.isEmpty()) {
            p.sendMessage(m().get("poll.none-active"));
            if (isAdmin(p)) openManage(p);
        } else if (polls.size() == 1) {
            show(p, ctx.dialogs.vote(polls.getFirst(), p));
        } else {
            show(p, ctx.dialogs.list(polls, p, isAdmin(p)));
        }
    }

    /** The menu popping up on its own; quietly does nothing if the player no longer qualifies. */
    public void autoOpen(Player p, List<Long> ids) {
        if (!p.isOnline() || AutoOpener.isOptedOut(p)) return;
        List<Poll> polls = new ArrayList<>();
        for (long id : ids) {
            Optional<Poll> poll = ctx.polls.openPoll(id);
            if (poll.isPresent() && poll.get().isActive() && canVote(p, poll.get())
                    && !ctx.polls.hasVoted(poll.get(), p.getUniqueId())) {
                polls.add(poll.get());
            }
        }
        if (polls.isEmpty()) return;
        if (polls.size() == 1) p.showDialog(ctx.dialogs.vote(polls.getFirst(), p));
        else p.showDialog(ctx.dialogs.list(polls, p, false));
    }

    /** Opens the vote menu of a running poll, or the results of a finished one. */
    public void openPoll(Player p, long id) {
        ctx.polls.find(id).whenComplete((found, t) -> {
            if (t != null) {
                fail(p, t, "Cannot load poll #" + id);
            } else if (found.isEmpty()) {
                onPlayer(p, () -> p.sendMessage(m().get("error.unknown-poll", Messages.text("id", Long.toString(id)))));
            } else {
                Poll poll = found.get();
                if (poll.isActive()) {
                    onPlayer(p, () -> {
                        if (canVote(p, poll)) show(p, ctx.dialogs.vote(poll, p));
                        else p.sendMessage(m().get("vote.no-permission"));
                    });
                } else {
                    openResults(p, poll, null);
                }
            }
        });
    }

    /** Results of any poll. Running polls only show numbers the voting settings allow (staff always may). */
    public void openResults(Player p, Poll poll, io.papermc.paper.registry.data.dialog.action.DialogAction back) {
        ctx.polls.result(poll).whenComplete((result, t) -> {
            if (t != null) {
                fail(p, t, "Cannot load results of poll #" + poll.id);
            } else {
                onPlayer(p, () -> {
                    if (!resultsAllowed(p, poll)) p.sendMessage(m().get("poll.results-hidden"));
                    else show(p, ctx.dialogs.results(result, back));
                });
            }
        });
    }

    public boolean resultsAllowed(CommandSender who, Poll poll) {
        if (!poll.isActive() || isAdmin(who)) return true;
        Settings.ResultsVisibility v = s().voting().results();
        if (v == Settings.ResultsVisibility.LIVE) return true;
        return v == Settings.ResultsVisibility.AFTER_VOTE && who instanceof Player p && ctx.polls.hasVoted(poll, p.getUniqueId());
    }

    public void openHistory(Player p) {
        ctx.polls.recent(20).whenComplete((polls, t) -> {
            if (t != null) fail(p, t, "Cannot load the poll history");
            else onPlayer(p, () -> {
                List<Poll> visible = polls.stream().filter(x -> x.status() != PollStatus.CANCELLED || isAdmin(p)).toList();
                if (visible.isEmpty()) p.sendMessage(m().get("poll.history-empty"));
                else show(p, ctx.dialogs.history(visible));
            });
        });
    }

    public void openCreate(Player p) {
        if (deny(p, p.hasPermission("justpolls.create"))) return;
        show(p, ctx.dialogs.create(CreateForm.empty(s()), null));
    }

    public void openManage(Player p) {
        if (deny(p, isAdmin(p))) return;
        show(p, ctx.dialogs.manage(ctx.polls.openPolls()));
    }

    private void openAdmin(Player p, long id) {
        ctx.polls.find(id).whenComplete((found, t) -> {
            if (t != null) {
                fail(p, t, "Cannot load poll #" + id);
            } else if (found.isEmpty()) {
                onPlayer(p, () -> p.sendMessage(m().get("error.unknown-poll", Messages.text("id", Long.toString(id)))));
            } else {
                ctx.polls.result(found.get()).whenComplete((r, t2) -> {
                    if (t2 != null) fail(p, t2, "Cannot load results");
                    else onPlayer(p, () -> show(p, ctx.dialogs.admin(found.get(), r.total())));
                });
            }
        });
    }

    // --- clicks ----------------------------------------------------------------------------------------------

    public void handle(Player p, Key key, ClickData tag) {
        if (key.equals(Clicks.VOTE)) vote(p, tag.number("p", -1), (int) tag.number("o", -1));
        else if (key.equals(Clicks.VIEW)) openPoll(p, tag.number("p", -1));
        else if (key.equals(Clicks.ADMIN)) admin(p, tag);
        else if (key.equals(Clicks.CREATE)) createFromDialog(p, tag);
    }

    public void vote(Player p, long pollId, int option) {
        Poll poll = ctx.polls.openPoll(pollId).orElse(null);
        if (poll == null || !poll.isActive()) {
            p.sendMessage(m().get("vote.closed"));
            return;
        }
        if (!canVote(p, poll)) {
            p.sendMessage(m().get("vote.no-permission"));
            return;
        }
        if (option < 0 || option >= poll.options.size()) return;
        ctx.polls.vote(poll, p.getUniqueId(), option, s().voting().allowChange()).whenComplete((outcome, t) -> {
            if (t != null) {
                fail(p, t, "Cannot record a vote in poll #" + pollId);
                return;
            }
            String label = poll.options.get(option);
            onPlayer(p, () -> {
                switch (outcome) {
                    case VOTED, CHANGED -> {
                        ctx.autoOpener.forget(p.getUniqueId(), pollId);
                        p.sendMessage(m().get(outcome == VoteOutcome.VOTED ? "vote.success" : "vote.changed", Messages.text("option", label)));
                        if (outcome == VoteOutcome.VOTED) runRewards(p);
                        afterVote(p, poll, label);
                    }
                    case ALREADY_VOTED -> {
                        p.sendMessage(m().get("vote.already"));
                        show(p, ctx.dialogs.vote(poll, p));
                    }
                    case CLOSED -> p.sendMessage(m().get("vote.closed"));
                    case INVALID_OPTION -> { }
                }
            });
        });
    }

    private void afterVote(Player p, Poll poll, String label) {
        if (resultsAllowed(p, poll)) {
            show(p, ctx.dialogs.results(new PollResult(poll, poll.counts()), null));
        } else {
            show(p, ctx.dialogs.notice(Component.text(poll.title, net.kyori.adventure.text.format.NamedTextColor.GOLD),
                    List.of(m().get("gui.vote.thanks"), m().get("gui.vote.you-voted", Messages.text("option", label)),
                            m().get("gui.vote.results-hidden")), null));
        }
    }

    private void runRewards(Player p) {
        List<String> commands = s().voting().rewardCommands();
        if (commands.isEmpty()) return;
        ctx.scheduler.runGlobal(() -> {
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
        });
    }

    private void admin(Player p, ClickData tag) {
        String action = tag.text("a");
        long id = tag.number("p", -1);
        if (action.equals("new")) {
            openCreate(p);
            return;
        }
        if (deny(p, isAdmin(p))) return;
        switch (action) {
            case "list" -> openManage(p);
            case "history" -> openHistory(p);
            case "view" -> openAdmin(p, id);
            case "results" -> ctx.polls.find(id).thenAccept(found -> found.ifPresent(poll ->
                    openResults(p, poll, Clicks.admin("view", id))));
            case "end", "cancel", "delete" -> ctx.polls.find(id).thenAccept(found -> found.ifPresentOrElse(
                    poll -> onPlayer(p, () -> show(p, ctx.dialogs.confirm(action, poll, Clicks.admin("do-" + action, id)))),
                    () -> onPlayer(p, () -> p.sendMessage(m().get("error.unknown-poll", Messages.text("id", Long.toString(id)))))));
            case "do-end" -> end(p, id).thenRun(() -> onPlayer(p, () -> openManage(p)));
            case "do-cancel" -> cancel(p, id).thenRun(() -> onPlayer(p, () -> openManage(p)));
            case "do-delete" -> delete(p, id).thenRun(() -> onPlayer(p, () -> openManage(p)));
            case "extend" -> extend(p, id, tag.number("n", 0)).thenRun(() -> openAdmin(p, id));
            case "open-all" -> {
                openForAll(p, id);
                openAdmin(p, id);
            }
            case "webhook" -> webhook(p, id).thenRun(() -> openAdmin(p, id));
            default -> { }
        }
    }

    private void createFromDialog(Player p, ClickData tag) {
        if (deny(p, p.hasPermission("justpolls.create"))) return;
        CreateForm form = CreateForm.from(tag, s().creation().maxOptions());
        create(p, form, error -> onPlayer(p, () -> show(p, ctx.dialogs.create(form, error))));
    }

    // --- operations shared by menus and commands ---------------------------------------------------------------

    /** Validates the form and creates the poll; {@code onError} gets a message for the creator if it is rejected. */
    public void create(CommandSender sender, CreateForm form, Consumer<Component> onError) {
        CreateForm.Validation v = form.validate(s(), m(), sender.getName());
        if (v.poll() == null) {
            onError.accept(v.error());
            return;
        }
        if (ctx.polls.openCount() >= s().creation().maxActive()) {
            onError.accept(m().get("create.limit", Messages.text("max", Integer.toString(s().creation().maxActive()))));
            return;
        }
        NewPoll poll = v.poll();
        ctx.polls.create(poll).whenComplete((created, t) -> {
            if (t != null) {
                ctx.log.log(Level.WARNING, "Cannot create the poll", t);
                sender.sendMessage(m().get("error.generic"));
                return;
            }
            boolean scheduled = created.status() == PollStatus.SCHEDULED;
            sender.sendMessage(m().get(scheduled ? "create.scheduled" : "create.success",
                    Messages.text("id", Long.toString(created.id)), Messages.text("title", created.title),
                    Messages.text("time", Durations.format(poll.durationMs()))));
        });
    }

    public CompletableFuture<Void> end(CommandSender who, long id) {
        return ctx.polls.endNow(id).thenAccept(ok -> who.sendMessage(ok
                ? m().get("manage.ended", Messages.text("id", Long.toString(id)))
                : m().get("error.not-open", Messages.text("id", Long.toString(id)))));
    }

    public CompletableFuture<Void> cancel(CommandSender who, long id) {
        return ctx.polls.cancel(id).thenAccept(ok -> who.sendMessage(ok
                ? m().get("manage.cancelled", Messages.text("id", Long.toString(id)))
                : m().get("error.not-open", Messages.text("id", Long.toString(id)))));
    }

    public CompletableFuture<Void> delete(CommandSender who, long id) {
        return ctx.polls.delete(id).thenAccept(ok -> who.sendMessage(ok
                ? m().get("manage.deleted", Messages.text("id", Long.toString(id)))
                : m().get("error.unknown-poll", Messages.text("id", Long.toString(id)))));
    }

    public CompletableFuture<Void> extend(CommandSender who, long id, long deltaMs) {
        return ctx.polls.extend(id, deltaMs).thenAccept(ok -> {
            Optional<Poll> poll = ctx.polls.openPoll(id);
            if (ok && poll.isPresent()) {
                who.sendMessage(m().get("manage.extended", Messages.text("id", Long.toString(id)),
                        Messages.text("time", Durations.format(poll.get().endAt() - System.currentTimeMillis()))));
            } else {
                who.sendMessage(m().get("manage.extend-failed", Messages.text("id", Long.toString(id))));
            }
        });
    }

    /** Pops the vote menu up for every online player who may still vote. Returns how many. */
    public int openForAll(CommandSender who, long id) {
        Poll poll = ctx.polls.openPoll(id).orElse(null);
        if (poll == null || !poll.isActive()) {
            who.sendMessage(m().get("error.not-open", Messages.text("id", Long.toString(id))));
            return 0;
        }
        int[] count = {0};
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!canVote(target, poll) || ctx.polls.hasVoted(poll, target.getUniqueId())) continue;
            count[0]++;
            onPlayer(target, () -> target.showDialog(ctx.dialogs.vote(poll, target)));
        }
        who.sendMessage(m().get("manage.opened", Messages.text("count", Integer.toString(count[0]))));
        return count[0];
    }

    /** Sends (again) the results of a finished poll to Discord. */
    public CompletableFuture<Void> webhook(CommandSender who, long id) {
        Audience audience = who;
        if (!ctx.discord.enabled()) {
            audience.sendMessage(m().get("manage.webhook-off"));
            return CompletableFuture.completedFuture(null);
        }
        return ctx.polls.find(id).thenCompose(found -> {
            if (found.isEmpty()) {
                audience.sendMessage(m().get("error.unknown-poll", Messages.text("id", Long.toString(id))));
                return CompletableFuture.<Void>completedFuture(null);
            }
            Poll poll = found.get();
            if (poll.status() != PollStatus.ENDED) {
                audience.sendMessage(m().get("manage.webhook-not-ended"));
                return CompletableFuture.<Void>completedFuture(null);
            }
            audience.sendMessage(m().get("manage.webhook-queued", Messages.text("id", Long.toString(id))));
            return ctx.polls.result(poll).thenCompose(ctx.discord::sendResults).thenCompose(ok -> {
                audience.sendMessage(m().get(ok ? "manage.webhook-sent" : "manage.webhook-failed",
                        Messages.text("id", Long.toString(id)), Messages.text("label", ctx.plugin.commandLabel())));
                return ok ? ctx.polls.markWebhookSent(poll) : CompletableFuture.<Void>completedFuture(null);
            });
        });
    }
}
