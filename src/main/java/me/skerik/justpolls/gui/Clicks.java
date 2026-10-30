package me.skerik.justpolls.gui;

import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.jspecify.annotations.Nullable;

/**
 * Identifiers and payloads of every clickable thing in the dialogs. Buttons do not carry callbacks, they carry a
 * key plus a small compound ({@code {p:5L,o:2}}), so a dialog that stays open for hours still works and nothing
 * piles up in memory. The server never trusts the payload: permissions and poll state are checked again on click.
 */
public final class Clicks {

    /** {@code {p: poll id, o: option index}}: cast a vote. */
    public static final Key VOTE = Key.key("justpolls", "vote");
    /** {@code {p: poll id}}: open a poll (vote menu if running, results otherwise). */
    public static final Key VIEW = Key.key("justpolls", "view");
    /** {@code {a: action, p: poll id, ...}}: staff actions. */
    public static final Key ADMIN = Key.key("justpolls", "admin");
    /** Submit of the poll creation form (payload = the form inputs). */
    public static final Key CREATE = Key.key("justpolls", "create");

    private Clicks() {
    }

    public static DialogAction vote(long poll, int option) {
        return DialogAction.customClick(VOTE, tag("{p:" + poll + "L,o:" + option + "}"));
    }

    public static DialogAction view(long poll) {
        return DialogAction.customClick(VIEW, tag("{p:" + poll + "L}"));
    }

    public static DialogAction admin(String action, long poll) {
        return DialogAction.customClick(ADMIN, tag("{a:\"" + action + "\",p:" + poll + "L}"));
    }

    /** Staff action with a number argument ({@code n}, e.g. milliseconds) or the confirmation flag. */
    public static DialogAction admin(String action, long poll, String extraKey, long extraValue) {
        return DialogAction.customClick(ADMIN, tag("{a:\"" + action + "\",p:" + poll + "L," + extraKey + ":" + extraValue + "L}"));
    }

    public static DialogAction admin(String action) {
        return DialogAction.customClick(ADMIN, tag("{a:\"" + action + "\",p:0L}"));
    }

    public static DialogAction create() {
        return DialogAction.customClick(CREATE, null);
    }

    private static BinaryTagHolder tag(String snbt) {
        return BinaryTagHolder.binaryTagHolder(snbt);
    }

    /** Parses a click payload; anything unreadable becomes an empty payload (and so a harmless no-op). */
    public static ClickData parse(@Nullable BinaryTagHolder holder) {
        return holder == null ? ClickData.empty() : ClickData.parse(holder.string());
    }
}
