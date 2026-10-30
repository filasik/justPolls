package me.skerik.justpolls.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.model.NewPoll;
import me.skerik.justpolls.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

/** The values typed into the creation menu, with the validation shared by the menu and /justpolls quick. */
public record CreateForm(String title, String description, List<String> options, String duration,
                         String customDuration, boolean discord, boolean autoOpen) {

    public static final String CUSTOM = "custom";

    /** Outcome of {@link #validate}: either a poll to create or a message for the creator. */
    public record Validation(@Nullable NewPoll poll, @Nullable Component error) {
    }

    public static CreateForm empty(Settings s) {
        return new CreateForm("", "", List.of(), s.creation().defaultPreset(), "", s.discord().defaultSend(), true);
    }

    public static CreateForm from(ClickData tag, int maxOptions) {
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= maxOptions; i++) options.add(tag.text( "option" + i));
        return new CreateForm(tag.text( "title"), tag.text( "description"), options,
                tag.text( "duration"), tag.text( "custom_duration"),
                tag.bool( "discord"), tag.bool( "auto_open"));
    }

    /** Option {@code i} (0-based) as typed, or empty. */
    public String option(int i) {
        return i < options.size() ? options.get(i) : "";
    }

    public Validation validate(Settings s, Messages m, String creator) {
        String t = title.strip();
        if (t.isEmpty()) return fail(m, "create.title-required");
        if (t.length() > Settings.TITLE_MAX) return fail(m, "create.title-too-long", Messages.text("max", Integer.toString(Settings.TITLE_MAX)));
        String d = description.strip();
        if (d.length() > Settings.DESCRIPTION_MAX) return fail(m, "create.description-too-long", Messages.text("max", Integer.toString(Settings.DESCRIPTION_MAX)));
        List<String> cleaned = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String o : options) {
            String option = o.strip();
            if (option.isEmpty()) continue;
            if (option.length() > Settings.OPTION_MAX) return fail(m, "create.option-too-long", Messages.text("max", Integer.toString(Settings.OPTION_MAX)));
            if (!seen.add(option.toLowerCase(Locale.ROOT))) return fail(m, "create.options-duplicate");
            cleaned.add(option);
        }
        if (cleaned.size() < 2) return fail(m, "create.options-needed");
        OptionalLong ms = Durations.parse(duration.equals(CUSTOM) ? customDuration : duration);
        if (ms.isEmpty()) return fail(m, "create.duration-invalid");
        Settings.Creation c = s.creation();
        if (ms.getAsLong() < c.minDurationMs()) return fail(m, "create.duration-short", Messages.text("min", Durations.format(c.minDurationMs())));
        if (ms.getAsLong() > c.maxDurationMs()) return fail(m, "create.duration-long", Messages.text("max", Durations.format(c.maxDurationMs())));
        return new Validation(new NewPoll(t, d, cleaned, ms.getAsLong(), 0, discord && s.discord().enabled(), autoOpen,
                -1, "", null, 0, creator), null);
    }

    private static Validation fail(Messages m, String key, TagResolver... r) {
        return new Validation(null, m.get(key, r));
    }

    /**
     * Parses {@code <duration> <question> | <option> | <option>...} (the /justpolls quick syntax) into a form.
     * Returns null when the text has no duration, question or at least two options.
     */
    public static @Nullable CreateForm parseQuick(String text, Settings s) {
        String[] parts = text.strip().split("\\s*\\|\\s*");
        if (parts.length < 3) return null;
        String head = parts[0].strip();
        int space = head.indexOf(' ');
        if (space < 0) return null;
        String duration = head.substring(0, space);
        String title = head.substring(space + 1).strip();
        List<String> options = new ArrayList<>(List.of(parts).subList(1, parts.length));
        return new CreateForm(title, "", options, CUSTOM, duration, s.discord().defaultSend(), true);
    }
}
