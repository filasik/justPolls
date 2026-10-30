package me.skerik.justpolls;

import me.skerik.justpolls.command.MainCommand;
import me.skerik.justpolls.config.ConfigUpdater;
import me.skerik.justpolls.config.Messages;
import me.skerik.justpolls.config.Settings;
import me.skerik.justpolls.util.Banner;
import me.skerik.justpolls.util.Stats;
import me.skerik.justpolls.util.UpdateChecker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import me.skerik.justpolls.storage.Database;
import me.skerik.justpolls.storage.Migrations;
import me.skerik.justpolls.storage.PollStore;
import me.skerik.justpolls.service.PollService;
import me.skerik.justpolls.gui.Clicks;
import me.skerik.justpolls.model.Poll;
import me.skerik.justpolls.model.PollResult;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.bukkit.event.player.PlayerQuitEvent;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Startup order (keep it, it is the point of this class):
 * <ol>
 *   <li>{@code onEnable}, main thread, must stay fast: config (+ automatic update), settings, messages, scheduler,
 *       command registration, listeners.</li>
 *   <li>Slow or blocking work (database open + migrations) goes to an async task, never to the main thread.</li>
 *   <li>{@link #finishStartup()} runs on the first tick, after every other plugin has enabled: look up other
 *       plugins' services here, build the {@link Context}, start timers, print the banner.</li>
 * </ol>
 * A fatal problem (database unreachable, required service missing) logs one clear line and disables the plugin;
 * it never throws a stack trace into the console for something an admin has to fix.
 */
public final class JustPolls extends JavaPlugin implements Listener {

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile @Nullable Context context;
    private Scheduler scheduler;
    private @Nullable UpdateChecker updater;
    /** Cancel handles of repeating tasks; run all of them on disable. */
    private final List<Runnable> timers = new CopyOnWriteArrayList<>();
    private volatile boolean shuttingDown;
    private long enableStarted;
    private @Nullable Database database;
    private int schemaVersion;
    private @Nullable PollService pollService;

    @Override
    public void onEnable() {
        enableStarted = System.nanoTime();
        saveDefaultConfig();
        if (ConfigUpdater.update(this) > 0) reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.locale());
        scheduler = new PaperScheduler(this);

        List<String> aliases = settings.aliases();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new MainCommand(this).build(), "JustPolls commands", aliases));
        getServer().getPluginManager().registerEvents(this, this);

        scheduler.runAsync(this::openDatabase);
    }

    private void openDatabase() {
        try {
            Database db = new Database(settings.storage().dbConfig(getDataFolder().toPath()));
            schemaVersion = Migrations.migrate(db);
            database = db;
            PollService service = new PollService(new PollStore(db), System::currentTimeMillis, new Bridge(), getLogger());
            service.init();
            pollService = service;
            scheduler.runGlobal(this::finishStartup);
        } catch (SQLException | RuntimeException e) {
            String hint = String.valueOf(e.getMessage()).contains("Self signed")
                    ? " Hint: set storage.mariadb.ssl-mode to \"trust\" in config.yml (MySQL with a self-signed certificate)."
                    : "";
            getLogger().log(Level.SEVERE, "Cannot open the database: " + e.getMessage()
                    + ". Check storage settings in config.yml." + hint + " JustPolls is disabling itself.", e);
            scheduler.runGlobal(() -> Bukkit.getPluginManager().disablePlugin(this));
        }
    }

    /** Runs on the first tick: every other plugin has enabled by now. */
    private void finishStartup() {
        if (shuttingDown) return;
        // Look up services of other plugins here and disable with one clear line if a required one is missing.

        PollService service = java.util.Objects.requireNonNull(pollService);
        Context ctx = new Context(this, scheduler, settings, messages, service);
        context = ctx;

        // Polls start and end on time; the menu pops up when due; running polls are re-read for shared databases
        timers.add(scheduler.runGlobalTimer(service::tick, 20, 20));
        timers.add(scheduler.runGlobalTimer(ctx.autoOpener::tick, 40, 40));
        long syncTicks = settings.storage().syncSeconds() * 20L;
        timers.add(scheduler.runAsyncTimer(this::syncPolls, syncTicks, syncTicks));
        applyConfigPolls();
        retryPendingWebhooks(ctx);

        if (settings.updateCheck()) {
            updater = new UpdateChecker(getPluginMeta().getVersion(), getLogger());
            scheduler.runAsync(updater::check);
            timers.add(scheduler.runAsyncTimer(updater::check, 12L * 60 * 60 * 20, 12L * 60 * 60 * 20));
        }
        boolean stats = Stats.start(this, settings);
        if (!stats) getLogger().fine("bStats not started (disabled in config.yml or no bStats id in this build)");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Storage", (settings.storage().type().equals("sqlite") ? "SQLite" : "MariaDB") + " (schema v" + schemaVersion + ")");
        details.put("Language", settings.locale() + "   Updates " + (settings.updateCheck() ? "on" : "off"));
        details.put("Polls", service.openCount() + " open   Discord " + (settings.discord().enabled() ? "on" : "off"));
        Banner.print(getComponentLogger(), getPluginMeta().getVersion(), getServer().getMinecraftVersion(), details,
                (System.nanoTime() - enableStarted) / 1_000_000);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Context ctx = context;
        if (ctx == null) return;
        Player player = event.getPlayer();
        ctx.autoOpener.playerJoined(player);
        UpdateChecker u = updater;
        if (u != null && u.newerVersion() != null && player.hasPermission("justpolls.admin")) {
            scheduler.runForEntity(player, () -> player.sendMessage(ctx.messages().get("update.available",
                    Messages.text("version", String.valueOf(u.newerVersion())),
                    Messages.arg("url", String.valueOf(u.url())))), null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Context ctx = context;
        if (ctx != null) ctx.autoOpener.forgetPlayer(event.getPlayer().getUniqueId());
    }

    /** Every click in a JustPolls dialog (vote, staff actions, creation form) arrives here. */
    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().namespace().equals("justpolls")) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Context ctx = context;
        if (ctx == null) return;
        DialogResponseView view = event.getDialogResponseView();
        BinaryTagHolder payload = view != null ? view.payload() : event.getTag();
        me.skerik.justpolls.gui.ClickData tag = Clicks.parse(payload);
        ctx.controller.handle(connection.getPlayer(), event.getIdentifier(), tag);
    }

    /** Creates due polls from the polls section of config.yml (once each; see PollService.applyConfigPolls). */
    private void applyConfigPolls() {
        PollService service = pollService;
        if (service != null && !shuttingDown) service.applyConfigPolls(settings.configPolls());
    }

    private void syncPolls() {
        PollService service = pollService;
        if (service == null || shuttingDown) return;
        service.refresh().thenCompose(v -> service.applyConfigPolls(settings.configPolls())).exceptionally(t -> {
            getLogger().log(Level.WARNING, "Cannot refresh polls from the database", t);
            return null;
        });
    }

    /** Results that could not be delivered earlier (crash, Discord down) are sent now that Discord is available. */
    private void retryPendingWebhooks(Context ctx) {
        if (!settings.discord().enabled()) return;
        ctx.polls.pendingWebhooks().thenAccept(pending -> {
            for (PollResult r : pending) ctx.announcer.deliverResults(r);
        }).exceptionally(t -> {
            getLogger().log(Level.WARNING, "Cannot look for undelivered Discord results", t);
            return null;
        });
    }

    /** Poll events reach the announcer once startup has finished. */
    private final class Bridge implements PollService.Events {
        @Override
        public void started(Poll poll) {
            Context ctx = context;
            if (ctx != null) ctx.announcer.started(poll);
        }

        @Override
        public void ended(PollResult result) {
            Context ctx = context;
            if (ctx != null) ctx.announcer.ended(result);
        }

        @Override
        public void cancelled(Poll poll) {
            Context ctx = context;
            if (ctx != null) ctx.announcer.cancelled(poll);
        }
    }

    /** What the help texts call the command: the first alias, or the full name. */
    public String commandLabel() {
        List<String> aliases = settings.aliases();
        return aliases.isEmpty() ? "justpolls" : aliases.getFirst();
    }

    /** /justpolls reload: config.yml values and messages. Storage settings and command aliases need a restart. */
    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.locale());
        Context ctx = context;
        if (ctx != null) {
            ctx.update(settings, messages);
            scheduler.runAsync(this::applyConfigPolls);
        }
    }

    public @Nullable Context context() {
        return context;
    }

    public Messages messages() {
        return messages;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        timers.forEach(Runnable::run);
        timers.clear();
        context = null;
        if (database != null) database.close();
        getComponentLogger().info(net.kyori.adventure.text.Component.text("JustPolls disabled. Goodbye!",
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }
}
