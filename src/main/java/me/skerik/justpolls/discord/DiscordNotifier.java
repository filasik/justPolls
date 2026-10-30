package me.skerik.justpolls.discord;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import me.skerik.justpolls.Context;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;

/** Sends poll messages to Discord on the async scheduler, using the settings and language that are current. */
public final class DiscordNotifier {

    private final Context ctx;
    private final WebhookSender sender;

    public DiscordNotifier(Context ctx) {
        this.ctx = ctx;
        this.sender = new WebhookSender(ctx.log);
    }

    public boolean enabled() {
        return ctx.settings().discord().enabled();
    }

    private WebhookMessages messages() {
        return new WebhookMessages(ctx.settings().discord(), ctx.messages()::format,
                Locale.forLanguageTag(ctx.settings().locale().replace('_', '-')));
    }

    public CompletableFuture<Boolean> sendResults(PollResult result) {
        return post(() -> messages().results(result));
    }

    public CompletableFuture<Boolean> test() {
        return post(() -> messages().test());
    }

    /** Fire and forget: the "poll started" message is a nicety, a failure is only logged. */
    public void announceStart(Poll poll) {
        if (!enabled() || !poll.discord || !ctx.settings().discord().announceStart()) return;
        post(() -> messages().started(poll));
    }

    private CompletableFuture<Boolean> post(java.util.function.Supplier<JsonObject> payload) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        String url = ctx.settings().discord().url();
        ctx.scheduler.runAsync(() -> {
            try {
                done.complete(sender.send(url, payload.get()));
            } catch (RuntimeException e) {
                ctx.log.log(Level.WARNING, "Cannot build or send the Discord message", e);
                done.complete(false);
            }
        });
        return done;
    }
}
