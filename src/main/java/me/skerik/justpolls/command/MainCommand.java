package me.skerik.justpolls.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.JustPolls;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.gui.AutoOpener;
import me.skerik.justpolls.gui.CreateForm;
import me.skerik.justpolls.gui.ResultView;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.util.Durations;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /justpolls and its subcommands (Brigadier). Aliases come from config.yml. */
public final class MainCommand {

    private final JustPolls plugin;
    /** Suggests the ids of running and scheduled polls. */
    private final SuggestionProvider<CommandSourceStack> pollIds;

    public MainCommand(JustPolls plugin) {
        this.plugin = plugin;
        this.pollIds = (c, builder) -> {
            Context ctx = plugin.context();
            if (ctx != null) ctx.polls.openPolls().forEach(p -> builder.suggest((int) p.id));
            return builder.buildFuture();
        };
    }

    private static boolean has(CommandSourceStack src, String permission) {
        return src.getSender().hasPermission(permission);
    }

    private ArgumentBuilder<CommandSourceStack, ?> pollCommand(String name, String permission, BiConsumer<CommandSender, Pair> action) {
        return Commands.literal(name)
                .requires(src -> has(src, permission))
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(pollIds)
                        .executes(c -> run(c, (ctx, sender) -> action.accept(sender, new Pair(ctx, IntegerArgumentType.getInteger(c, "id"))))));
    }

    /** Context plus poll id for the operations that take one. */
    private record Pair(Context ctx, int id) {
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralCommandNode<CommandSourceStack> root = buildRoot();
        // Test hook for the live test harness (bots cannot send dialog clicks through ViaVersion): console only,
        // off unless the server is started with -Djustpolls.debug=true, every permission check still applies to the target
        if (Boolean.getBoolean("justpolls.debug")) {
            root.addChild(Commands.literal("debug-click")
                    .requires(src -> !(src.getSender() instanceof Player))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("key", StringArgumentType.word())
                                    .then(Commands.argument("snbt", StringArgumentType.greedyString())
                                            .executes(c -> run(c, (ctx, sender) -> debugClick(ctx, sender,
                                                    StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "key"),
                                                    StringArgumentType.getString(c, "snbt")))))))
                    .build());
        }
        return root;
    }

    private void debugClick(Context ctx, CommandSender sender, String player, String key, String snbt) {
        Player target = org.bukkit.Bukkit.getPlayerExact(player);
        if (target == null) {
            sender.sendMessage(ctx.messages().get("error.no-target"));
            return;
        }
        ctx.scheduler.runForEntity(target, () -> ctx.controller.handle(target, net.kyori.adventure.key.Key.key("justpolls", key),
                me.skerik.justpolls.gui.ClickData.parse(snbt)), null);
    }

    private LiteralCommandNode<CommandSourceStack> buildRoot() {
        return Commands.literal("justpolls")
                .requires(src -> has(src, "justpolls.use"))
                .executes(c -> run(c, (ctx, sender) -> {
                    if (sender instanceof Player p) ctx.controller.openDefault(p);
                    else info(sender);
                }))
                .then(Commands.literal("vote")
                        .requires(src -> has(src, "justpolls.vote"))
                        .executes(c -> player(c, (ctx, p) -> ctx.controller.openDefault(p)))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(pollIds)
                                .executes(c -> player(c, (ctx, p) -> ctx.controller.openPoll(p, IntegerArgumentType.getInteger(c, "id"))))))
                .then(Commands.literal("list").executes(c -> run(c, this::list)))
                .then(Commands.literal("history").executes(c -> run(c, (ctx, sender) -> {
                    if (sender instanceof Player p) ctx.controller.openHistory(p);
                    else history(ctx, sender);
                })))
                .then(Commands.literal("results")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(pollIds)
                                .executes(c -> run(c, (ctx, sender) -> results(ctx, sender, IntegerArgumentType.getInteger(c, "id"))))))
                .then(Commands.literal("autoopen")
                        .executes(c -> player(c, (ctx, p) -> autoOpen(ctx, p, AutoOpener.isOptedOut(p))))
                        .then(Commands.literal("on").executes(c -> player(c, (ctx, p) -> autoOpen(ctx, p, true))))
                        .then(Commands.literal("off").executes(c -> player(c, (ctx, p) -> autoOpen(ctx, p, false)))))
                .then(Commands.literal("create")
                        .requires(src -> has(src, "justpolls.create"))
                        .executes(c -> player(c, (ctx, p) -> ctx.controller.openCreate(p))))
                .then(Commands.literal("quick")
                        .requires(src -> has(src, "justpolls.create"))
                        .then(Commands.argument("poll", StringArgumentType.greedyString())
                                .executes(c -> run(c, (ctx, sender) -> quick(ctx, sender, StringArgumentType.getString(c, "poll"))))))
                .then(Commands.literal("manage")
                        .requires(src -> has(src, "justpolls.admin"))
                        .executes(c -> player(c, (ctx, p) -> ctx.controller.openManage(p))))
                .then(pollCommand("end", "justpolls.admin", (s, p) -> p.ctx().controller.end(s, p.id())))
                .then(pollCommand("cancel", "justpolls.admin", (s, p) -> p.ctx().controller.cancel(s, p.id())))
                .then(pollCommand("delete", "justpolls.admin", (s, p) -> p.ctx().controller.delete(s, p.id())))
                .then(pollCommand("open", "justpolls.admin", (s, p) -> p.ctx().controller.openForAll(s, p.id())))
                .then(pollCommand("webhook", "justpolls.admin", (s, p) -> p.ctx().controller.webhook(s, p.id())))
                .then(Commands.literal("extend")
                        .requires(src -> has(src, "justpolls.admin"))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(pollIds)
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(c -> run(c, (ctx, sender) -> extend(ctx, sender,
                                                IntegerArgumentType.getInteger(c, "id"), StringArgumentType.getString(c, "duration")))))))
                .then(Commands.literal("webhook-test")
                        .requires(src -> has(src, "justpolls.admin"))
                        .executes(c -> run(c, this::webhookTest)))
                .then(Commands.literal("reload")
                        .requires(src -> has(src, "justpolls.admin"))
                        .executes(this::reload))
                .build();
    }

    // --- plumbing ----------------------------------------------------------------------------------------------

    /** Runs {@code action} once the plugin has started; before that every command answers "not ready". */
    private int run(CommandContext<CommandSourceStack> c, BiConsumer<Context, CommandSender> action) {
        CommandSender sender = c.getSource().getSender();
        Context ctx = plugin.context();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        action.accept(ctx, sender);
        return Command.SINGLE_SUCCESS;
    }

    /** Like {@link #run} for commands that need a player; the console gets "players only". */
    private int player(CommandContext<CommandSourceStack> c, BiConsumer<Context, Player> action) {
        return run(c, (ctx, sender) -> {
            if (sender instanceof Player p) action.accept(ctx, p);
            else sender.sendMessage(ctx.messages().get("error.players-only"));
        });
    }

    private TagResolver label() {
        return Messages.text("label", plugin.commandLabel());
    }

    // --- commands --------------------------------------------------------------------------------------------------

    private void info(CommandSender sender) {
        Messages m = plugin.messages();
        TagResolver[] r = {Messages.text("version", plugin.getPluginMeta().getVersion()), label()};
        m.list("command.info", r).forEach(sender::sendMessage);
        if (sender.hasPermission("justpolls.create")) m.list("command.info-admin", r).forEach(sender::sendMessage);
    }

    private void list(Context ctx, CommandSender sender) {
        Messages m = ctx.messages();
        List<Poll> polls = ctx.polls.openPolls().stream()
                .filter(p -> !(sender instanceof Player pl) || ctx.controller.canVote(pl, p)).toList();
        if (polls.isEmpty()) {
            sender.sendMessage(m.get("poll.none-active"));
            return;
        }
        sender.sendMessage(m.get("poll.list-header"));
        long now = System.currentTimeMillis();
        for (Poll p : polls) {
            boolean voted = sender instanceof Player pl && ctx.polls.hasVoted(p, pl.getUniqueId());
            String key = p.status() == PollStatus.SCHEDULED ? "poll.list-scheduled" : voted ? "poll.list-line-voted" : "poll.list-line";
            long until = p.status() == PollStatus.SCHEDULED ? p.startAt() - now : p.endAt() - now;
            sender.sendMessage(m.get(key, Messages.text("id", Long.toString(p.id)), Messages.text("title", p.title),
                    Messages.text("time", Durations.format(until)), label()));
        }
    }

    private void history(Context ctx, CommandSender sender) {
        Messages m = ctx.messages();
        ctx.polls.recent(15).thenAccept(polls -> {
            if (polls.isEmpty()) {
                sender.sendMessage(m.get("poll.history-empty"));
                return;
            }
            sender.sendMessage(m.get("poll.history-header"));
            for (Poll p : polls) {
                sender.sendMessage(m.get("poll.history-line", Messages.text("id", Long.toString(p.id)),
                        Messages.text("title", p.title), Messages.comp("status", ResultView.status(m, p)), label()));
            }
        });
    }

    private void results(Context ctx, CommandSender sender, int id) {
        Messages m = ctx.messages();
        ctx.polls.find(id).thenAccept(found -> {
            if (found.isEmpty()) {
                sender.sendMessage(m.get("error.unknown-poll", Messages.text("id", Integer.toString(id))));
                return;
            }
            Poll poll = found.get();
            if (!ctx.controller.resultsAllowed(sender, poll)) {
                sender.sendMessage(m.get("poll.results-hidden"));
                return;
            }
            ctx.polls.result(poll).thenAccept(r -> {
                sender.sendMessage(m.get("poll.results-header", Messages.text("title", poll.title),
                        Messages.comp("status", ResultView.status(m, poll)), Messages.text("total", Integer.toString(r.total()))));
                ResultView.lines(m, r, poll.status() == PollStatus.ENDED, false).forEach(sender::sendMessage);
            });
        });
    }

    private void autoOpen(Context ctx, Player p, boolean enabled) {
        AutoOpener.setOptedOut(p, !enabled);
        p.sendMessage(ctx.messages().get(enabled ? "autoopen.enabled" : "autoopen.disabled", label()));
    }

    private void quick(Context ctx, CommandSender sender, String text) {
        CreateForm form = CreateForm.parseQuick(text, ctx.settings());
        if (form == null) {
            sender.sendMessage(ctx.messages().get("command.quick-usage", label()));
            return;
        }
        ctx.controller.create(sender, form, sender::sendMessage);
    }

    private void extend(Context ctx, CommandSender sender, int id, String text) {
        boolean negative = text.startsWith("-");
        OptionalLong ms = Durations.parse(negative || text.startsWith("+") ? text.substring(1) : text);
        if (ms.isEmpty()) {
            sender.sendMessage(ctx.messages().get("error.bad-duration"));
            return;
        }
        ctx.controller.extend(sender, id, negative ? -ms.getAsLong() : ms.getAsLong());
    }

    private void webhookTest(Context ctx, CommandSender sender) {
        if (!ctx.discord.enabled()) {
            sender.sendMessage(ctx.messages().get("manage.webhook-off"));
            return;
        }
        ctx.discord.test().thenAccept(ok -> sender.sendMessage(ctx.messages().get(ok ? "manage.webhook-test-ok" : "manage.webhook-test-fail")));
    }

    private int reload(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        plugin.reloadSettings();
        sender.sendMessage(plugin.messages().get("command.reloaded"));
        return Command.SINGLE_SUCCESS;
    }
}
