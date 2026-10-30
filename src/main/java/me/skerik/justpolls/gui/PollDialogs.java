package me.skerik.justpolls.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import me.skerik.justpolls.model.PollStatus;
import me.skerik.justpolls.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Builds every dialog (the Minecraft 1.21.6+ menus with buttons and inputs). Pure construction: no I/O, no state;
 * what a click does is decided in {@link DialogController}. Texts come from the messages files, poll texts (typed
 * by players) are inserted as plain text, never parsed.
 */
public final class PollDialogs {

    private static final int WIDE = 300;
    private static final int BUTTON = 260;

    private final Context ctx;

    public PollDialogs(Context ctx) {
        this.ctx = ctx;
    }

    private Messages m() {
        return ctx.messages();
    }

    private static Dialog build(Component title, List<DialogBody> body, List<DialogInput> inputs, DialogType type) {
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).body(body).inputs(inputs).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private ActionButton close() {
        return ActionButton.create(m().get("gui.close"), null, 150, null);
    }

    private ActionButton button(Component label, @Nullable Component tooltip, int width, @Nullable DialogAction action) {
        return ActionButton.create(label, tooltip, width, action);
    }

    private static DialogBody text(Component c) {
        return DialogBody.plainMessage(c, 300);
    }

    private static Component pollTitle(Poll poll) {
        return Component.text(poll.title, NamedTextColor.GOLD);
    }

    // --- voting ----------------------------------------------------------------------------------------------

    /** The poll with its options as buttons, or (voted / ended) a read-only view with the results the settings allow. */
    public Dialog vote(Poll poll, Player viewer) {
        Messages m = m();
        Settings.Voting voting = ctx.settings().voting();
        boolean voted = ctx.polls.hasVoted(poll, viewer.getUniqueId());
        int mine = ctx.polls.votedOption(poll, viewer.getUniqueId());
        boolean canVote = poll.isActive() && (!voted || voting.allowChange());
        boolean showResults = !poll.isActive()
                || voting.results() == Settings.ResultsVisibility.LIVE
                || (voting.results() == Settings.ResultsVisibility.AFTER_VOTE && voted);

        List<DialogBody> body = new ArrayList<>();
        if (!poll.description.isBlank()) body.add(text(Component.text(poll.description, NamedTextColor.GRAY)));
        if (poll.isActive()) {
            body.add(text(m.get("gui.vote.ends", Messages.text("time", Durations.format(poll.endAt() - System.currentTimeMillis())))));
        } else {
            body.add(text(m.get("gui.vote.ended")));
        }
        if (voted && mine >= 0 && mine < poll.options.size()) {
            body.add(text(m.get("gui.vote.you-voted", Messages.text("option", poll.options.get(mine)))));
            if (canVote) body.add(text(m.get("gui.vote.can-change")));
        } else if (poll.isActive() && !showResults && voting.results() == Settings.ResultsVisibility.AFTER_END && voted) {
            body.add(text(m.get("gui.vote.results-hidden")));
        }
        if (showResults) {
            body.add(text(ResultView.block(m, new PollResult(poll, poll.counts()), !poll.isActive())));
        }

        DialogType type;
        if (canVote) {
            List<ActionButton> buttons = new ArrayList<>();
            for (int i = 0; i < poll.options.size(); i++) {
                String option = poll.options.get(i);
                Component label = m.get(i == mine ? "gui.vote.option-current" : "gui.vote.option", Messages.text("option", option));
                buttons.add(button(label, m.get("gui.vote.option-tooltip", Messages.text("option", option)), BUTTON, Clicks.vote(poll.id, i)));
            }
            type = DialogType.multiAction(buttons).exitAction(close()).columns(1).build();
        } else {
            type = DialogType.notice(close());
        }
        return build(pollTitle(poll), body, List.of(), type);
    }

    /** Several polls to choose from. */
    public Dialog list(List<Poll> polls, Player viewer, boolean admin) {
        Messages m = m();
        List<ActionButton> buttons = new ArrayList<>();
        for (Poll p : polls) {
            boolean voted = ctx.polls.hasVoted(p, viewer.getUniqueId());
            Component label = m.get(voted ? "gui.list.entry-voted" : "gui.list.entry", Messages.text("title", p.title),
                    Messages.text("time", Durations.format(p.endAt() - System.currentTimeMillis())));
            buttons.add(button(label, null, BUTTON, Clicks.view(p.id)));
        }
        if (admin) buttons.add(button(m.get("gui.list.manage"), null, BUTTON, Clicks.admin("list")));
        return build(m.get("gui.list.title"), List.of(text(m.get("gui.list.body"))), List.of(),
                DialogType.multiAction(buttons).exitAction(close()).columns(1).build());
    }

    /** Results with bars; {@code back} is the action of the extra button (null = only Close). */
    public Dialog results(PollResult r, @Nullable DialogAction back) {
        Messages m = m();
        Poll p = r.poll();
        List<DialogBody> body = new ArrayList<>();
        body.add(text(m.get(p.isActive() ? "gui.results.live" : "gui.results.final")));
        body.add(text(ResultView.block(m, r, p.status() == PollStatus.ENDED)));
        body.add(text(m.get("gui.results.total", Messages.text("total", Integer.toString(r.total())))));
        DialogType type = back == null
                ? DialogType.notice(close())
                : DialogType.notice(button(m.get("gui.back"), null, 150, back));
        return build(Component.empty().append(m.get("gui.results.title")).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(pollTitle(p)), body, List.of(), type);
    }

    /** A short message with a Close button (thanks for voting, poll ended, ...). */
    public Dialog notice(Component title, List<Component> lines, @Nullable DialogAction back) {
        List<DialogBody> body = new ArrayList<>();
        for (Component line : lines) body.add(text(line));
        DialogType type = back == null
                ? DialogType.notice(close())
                : DialogType.notice(button(m().get("gui.back"), null, 150, back));
        return build(title, body, List.of(), type);
    }

    public Dialog history(List<Poll> polls) {
        Messages m = m();
        List<ActionButton> buttons = new ArrayList<>();
        for (Poll p : polls) {
            buttons.add(button(m.get("gui.history.entry", Messages.text("title", p.title), Messages.comp("status", ResultView.status(m, p))),
                    null, BUTTON, Clicks.view(p.id)));
        }
        return build(m.get("gui.history.title"), List.of(), List.of(),
                DialogType.multiAction(buttons).exitAction(close()).columns(1).build());
    }

    // --- creating --------------------------------------------------------------------------------------------

    public Dialog create(CreateForm form, @Nullable Component error) {
        Messages m = m();
        Settings s = ctx.settings();
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(text(m.get("gui.create.error", Messages.comp("error", error))));
        body.add(text(m.get("gui.create.hint")));

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("title", m.get("gui.create.field-title")).width(WIDE)
                .initial(form.title()).maxLength(Settings.TITLE_MAX).build());
        inputs.add(DialogInput.text("description", m.get("gui.create.field-description")).width(WIDE)
                .initial(form.description()).maxLength(Settings.DESCRIPTION_MAX)
                .multiline(TextDialogInput.MultilineOptions.create(4, 60)).build());
        for (int i = 1; i <= s.creation().maxOptions(); i++) {
            inputs.add(DialogInput.text("option" + i, m.get(i <= 2 ? "gui.create.field-option-required" : "gui.create.field-option",
                            Messages.text("n", Integer.toString(i)))).width(WIDE)
                    .initial(form.option(i - 1)).maxLength(Settings.OPTION_MAX).build());
        }
        List<SingleOptionDialogInput.OptionEntry> durations = new ArrayList<>();
        for (String preset : s.creation().presets()) {
            durations.add(SingleOptionDialogInput.OptionEntry.create(preset, Component.text(preset), preset.equals(form.duration())));
        }
        durations.add(SingleOptionDialogInput.OptionEntry.create(CreateForm.CUSTOM, m.get("gui.create.custom"),
                form.duration().equals(CreateForm.CUSTOM)));
        inputs.add(DialogInput.singleOption("duration", m.get("gui.create.field-duration"), durations).width(WIDE).build());
        inputs.add(DialogInput.text("custom_duration", m.get("gui.create.field-custom-duration")).width(WIDE)
                .initial(form.customDuration()).maxLength(16).build());
        if (s.discord().enabled()) {
            inputs.add(DialogInput.bool("discord", m.get("gui.create.field-discord")).initial(form.discord()).build());
        }
        inputs.add(DialogInput.bool("auto_open", m.get("gui.create.field-auto-open")).initial(form.autoOpen()).build());

        ActionButton submit = button(m.get("gui.create.submit"), null, 150, Clicks.create());
        ActionButton cancel = button(m.get("gui.create.cancel"), null, 150, null);
        return build(m.get("gui.create.title"), body, inputs, DialogType.confirmation(submit, cancel));
    }

    // --- managing --------------------------------------------------------------------------------------------

    public Dialog manage(List<Poll> polls) {
        Messages m = m();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(m.get("gui.manage.new"), null, BUTTON, Clicks.admin("new")));
        for (Poll p : polls) {
            buttons.add(button(m.get("gui.manage.entry", Messages.text("id", Long.toString(p.id)), Messages.text("title", p.title),
                            Messages.comp("status", ResultView.status(m, p)), Messages.text("time", timeText(p))),
                    null, BUTTON, Clicks.admin("view", p.id)));
        }
        buttons.add(button(m.get("gui.manage.history"), null, BUTTON, Clicks.admin("history")));
        List<DialogBody> body = polls.isEmpty() ? List.of(text(m.get("gui.manage.empty"))) : List.of();
        return build(m.get("gui.manage.title"), body, List.of(),
                DialogType.multiAction(buttons).exitAction(close()).columns(1).build());
    }

    private static String timeText(Poll p) {
        long now = System.currentTimeMillis();
        return Durations.format(p.status() == PollStatus.SCHEDULED ? p.startAt() - now : p.endAt() - now);
    }

    public Dialog admin(Poll poll, int totalVotes) {
        Messages m = m();
        String id = Long.toString(poll.id);
        String time = switch (poll.status()) {
            case SCHEDULED -> m.format("gui.admin.time-scheduled", "left", Durations.format(poll.startAt() - System.currentTimeMillis()));
            case ACTIVE -> m.format("gui.admin.time-active", "left", Durations.format(poll.endAt() - System.currentTimeMillis()));
            default -> m.format("gui.admin.time-ended");
        };
        List<DialogBody> body = new ArrayList<>();
        for (Component line : m.list("gui.admin.info",
                Messages.text("title", poll.title), Messages.comp("status", ResultView.status(m, poll)),
                Messages.text("creator", poll.createdBy), Messages.text("time", time),
                Messages.text("total", Integer.toString(totalVotes)),
                Messages.text("discord", m.format(poll.discord ? "gui.admin.value-yes" : "gui.admin.value-no")),
                Messages.text("auto", m.format(poll.autoOpen ? "gui.admin.value-yes" : "gui.admin.value-no")))) {
            body.add(text(line));
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(m.get("gui.admin.results"), null, 140, Clicks.admin("results", poll.id)));
        if (poll.status().isOpen()) {
            long hour = 3_600_000L;
            buttons.add(button(m.get("gui.admin.extend", Messages.text("time", "1h")), null, 140, Clicks.admin("extend", poll.id, "n", hour)));
            buttons.add(button(m.get("gui.admin.extend", Messages.text("time", "1d")), null, 140, Clicks.admin("extend", poll.id, "n", 24 * hour)));
            buttons.add(button(m.get("gui.admin.shorten", Messages.text("time", "1h")), null, 140, Clicks.admin("extend", poll.id, "n", -hour)));
            if (poll.isActive()) {
                buttons.add(button(m.get("gui.admin.open-all"), null, 140, Clicks.admin("open-all", poll.id)));
                buttons.add(button(m.get("gui.admin.end"), null, 140, Clicks.admin("end", poll.id)));
            }
            buttons.add(button(m.get("gui.admin.cancel"), null, 140, Clicks.admin("cancel", poll.id)));
        } else if (poll.status() == PollStatus.ENDED && ctx.settings().discord().enabled()) {
            buttons.add(button(m.get("gui.admin.webhook"), null, 140, Clicks.admin("webhook", poll.id)));
        }
        buttons.add(button(m.get("gui.admin.delete"), null, 140, Clicks.admin("delete", poll.id)));
        ActionButton back = button(m.get("gui.back"), null, 150, Clicks.admin("list"));
        return build(m.get("gui.admin.title", Messages.text("id", id)), body, List.of(),
                DialogType.multiAction(buttons).exitAction(back).columns(2).build());
    }

    /** "Are you sure?" for end / cancel / delete. {@code confirmed} is the click that really does it. */
    public Dialog confirm(String what, Poll poll, DialogAction confirmed) {
        Messages m = m();
        TagResolver[] r = {Messages.text("id", Long.toString(poll.id)), Messages.text("title", poll.title)};
        ActionButton yes = button(m.get("gui.confirm.accept"), null, 150, confirmed);
        ActionButton no = button(m.get("gui.confirm.decline"), null, 150, Clicks.admin("view", poll.id));
        return build(m.get("gui.confirm.title"), List.of(text(m.get("gui.confirm." + what, r))), List.of(),
                DialogType.confirmation(yes, no));
    }
}
