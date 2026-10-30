package me.skerik.justpolls;

import java.util.logging.Logger;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.discord.DiscordNotifier;
import me.skerik.justpolls.gui.AutoOpener;
import me.skerik.justpolls.gui.DialogController;
import me.skerik.justpolls.gui.PollAnnouncer;
import me.skerik.justpolls.gui.PollDialogs;
import me.skerik.justpolls.service.PollService;

/**
 * Everything commands and listeners need, built once startup has finished (so it is null before that and
 * the plugin answers "not ready"). Settings and messages are swapped atomically on /justpolls reload.
 */
public final class Context {

    public final JustPolls plugin;
    public final Scheduler scheduler;
    public final Logger log;
    public final PollService polls;
    public final PollDialogs dialogs;
    public final DialogController controller;
    public final AutoOpener autoOpener;
    public final DiscordNotifier discord;
    public final PollAnnouncer announcer;

    private volatile Settings settings;
    private volatile Messages messages;

    /** The parts reference the context, so they are created here and receive it. */
    public Context(JustPolls plugin, Scheduler scheduler, Settings settings, Messages messages, PollService polls) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.log = plugin.getLogger();
        this.polls = polls;
        update(settings, messages);
        this.dialogs = new PollDialogs(this);
        this.autoOpener = new AutoOpener(this);
        this.discord = new DiscordNotifier(this);
        this.controller = new DialogController(this);
        this.announcer = new PollAnnouncer(this);
    }

    public void update(Settings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }
}
