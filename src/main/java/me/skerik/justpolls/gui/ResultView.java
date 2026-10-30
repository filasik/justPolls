package me.skerik.justpolls.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;

/** Turns counts into chat / dialog text: one line per option, optionally a bar below it. */
public final class ResultView {

    private static final int BAR_WIDTH = 24;

    private ResultView() {
    }

    public static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Lines for chat or a dialog. {@code highlightWinner} marks the winner(s), use it for finished polls only. */
    public static List<Component> lines(Messages m, PollResult r, boolean highlightWinner, boolean bars) {
        List<Component> out = new ArrayList<>();
        for (int i = 0; i < r.counts().length; i++) {
            boolean winner = highlightWinner && r.isWinner(i);
            out.add(m.get(winner ? "poll.results-line-winner" : "poll.results-line",
                    Messages.text("option", r.poll().options.get(i)),
                    Messages.text("percent", percent(r.percent(i))),
                    Messages.text("count", Integer.toString(r.counts()[i]))));
            if (bars) out.add(bar(r.percent(i), winner));
        }
        return out;
    }

    public static Component bar(double percent, boolean winner) {
        int filled = (int) Math.round(percent / 100.0 * BAR_WIDTH);
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        return Component.text("  " + "█".repeat(filled), winner ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                .append(Component.text("░".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY));
    }

    /** Whole block for a dialog body. */
    public static Component block(Messages m, PollResult r, boolean highlightWinner) {
        return Component.join(JoinConfiguration.newlines(), lines(m, r, highlightWinner, true));
    }

    public static Component status(Messages m, Poll poll) {
        return m.get("poll.status." + poll.status().name().toLowerCase(Locale.ROOT));
    }
}
