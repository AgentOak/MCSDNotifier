package me.agentoak.mcsdnotifier;

import me.agentoak.mcsdnotifier.system.SDNotify;
import me.agentoak.mcsdnotifier.system.SDNotifyException;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

public final class MCSDNotifierPlugin extends JavaPlugin {
    private static final long MSEC_PER_SEC = 1_000L;
    private static final long USEC_PER_MSEC = 1_000L;
    /**
     * In ms, default interval for watchdog/status updates to {@link SDNotify#watchdog(String)}. Used when the service
     * manager does not tell us the watchdog interval (usually when it does not have watchdog enabled).
     */
    private static final long DEFAULT_NOTIFY_INTERVAL = 10L * MSEC_PER_SEC;
    /**
     * In ms, upper cap for notify interval. If service manager sets watchdog interval to a higher value, we will
     * still report at this interval for up-to-date status messages.
     */
    private static final long MAX_NOTIFY_INTERVAL = 10L * MSEC_PER_SEC;
    /**
     * In ms, how long a tick is. Used as lower cap for notify interval. Minecraft is hardcoded to 20 ticks/sec.
     */
    private static final long TICK_INTERVAL = MSEC_PER_SEC / 20L;

    private SDNotify sdNotify;
    private long watchdogInterval;
    private long notifyInterval;

    private boolean sdNotifyEnabled;
    private NotifyListener notifyListener;

    private boolean hangStop;

    @Override
    public void onLoad() {
        /*
         * Some servers (notably Minecraft 1.17+) already include JNA, so we can't just shade the JNA classes into
         * our plugin JAR or there would be version conflicts depending on the class load order. However, JNA also
         * cannot be relocated (https://github.com/java-native-access/jna/issues/679). Loading a JNA jar dynamically is
         * tedious, just require the server to have JNA for now.
         */
        if (!ReflectionUtils.classExists("com.sun.jna.Native")) {
            getLogger().severe("JNA not found. Use a server that includes JNA (e.g. Minecraft 1.17+) or download " +
                                   "the latest JNA 5.x jar and put it into the Java claspath.");
            return;
        } else if (!SDNotify.isPlatformSupported()) {
            getLogger().severe("Not running on an sd_notify-aware service manager. This plugin has no effect");
            return;
        }

        try {
            sdNotify = new SDNotify();
        } catch (SDNotifyException e) {
            getLogger().log(Level.SEVERE, "Could not initialize sd_notify! If service manager expects notifications," +
                                              " the server may soon be considered unresponsive and killed!", e);
            return;
        }

        if (sdNotify.getWatchdogUsec() == 0) {
            getLogger().warning("Not running through service manager or watchdog is not configured, " +
                                    "hangs will not be detected!");
            notifyInterval = DEFAULT_NOTIFY_INTERVAL;
        } else {
            watchdogInterval = Math.max(1, sdNotify.getWatchdogUsec() / USEC_PER_MSEC);
            getLogger().info("Watchdog timeout is " + watchdogInterval + " ms");
            if (watchdogInterval < TICK_INTERVAL * 2) {
                getLogger().severe("Watchdog interval too low, we cannot send notifications this fast! " +
                                       "Should be at least two ticks (100ms)");
            }
            // General recommendation is to send updates at half the watchdog interval
            notifyInterval = Math.min(MAX_NOTIFY_INTERVAL, Math.max(TICK_INTERVAL, watchdogInterval / 2));
        }

        sdNotifyEnabled = true;

        /*
         * #onLoad is the earliest point in our plugin where we can run code, so get a status out as early as possible.
         * We don't know if the server is just starting or reloading, but sending the same MAINPID again should not do
         * any harm. Cannot consult StatusProvider here since plugins are not enabled yet.
         */
        getLogger().info("We are pid " + sdNotify.getPid() + " - notifying service manager. " +
                             "Sending watchdog updates every " + notifyInterval + " ms");
        sdNotify.init(null);
    }

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(StatusProvider.class,
            TPSStatusProvider.isSupported() ? new TPSStatusProvider(getServer()) : new BasicStatusProvider(getServer()),
            this, ServicePriority.Lowest);

        if (sdNotifyEnabled) {
            notifyListener = new NotifyListener(this, sdNotify);
            getServer().getPluginManager().registerEvents(notifyListener, this);
            // Bukkit/Spigot API lacks tick event so work around by running a task timer every tick.
            getServer().getScheduler().runTaskTimer(this, notifyListener::onTick, 1L, 1L);
        }

        installHangCommand("hang-main-and-accept-data-loss", () -> {
            getLogger().info("Deliberately hanging on main thread");
            hang();
        });

        installHangCommand("hang-stop-and-accept-data-loss", () -> {
            hangStop = true;
            getServer().shutdown();
        });
    }

    private void installHangCommand(String commandName, Runnable task) {
        getCommand(commandName).setExecutor((commandSender, command, s, strings) -> {
            if (!(commandSender instanceof ConsoleCommandSender)) {
                commandSender.sendMessage("This command can only be run from console");
                return true;
            }

            getServer().getScheduler().runTask(this, task);
            return true;
        });
    }

    @Override
    public void onDisable() {
        if (hangStop) {
            getLogger().info("Deliberately hanging during shutdown");
            hang();
        }

        // There is no way to defend against something disabling (or enabling) this plugin, so just warn if this happens
        if (sdNotifyEnabled && (notifyListener == null || !notifyListener.isTakedown())) {
            getLogger().warning(String.format("Plugin is being disabled but we did not detect a reload or stop. " +
                                                  "While plugin is disabled, notifications are not sent.%s " +
                                                  "If the server is reloading/stopping, you can ignore this message.",
                watchdogInterval == 0 ? "" : " To avoid triggering the watchdog never disable this plugin!"));
        }

        // Undo everything we may have done in onEnable, in case some server does not disable plugins properly
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
        getServer().getServicesManager().unregisterAll(this);
    }

    private static void hang() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // API Methods /////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether the plugin loaded successfully and is sending notifications.
     */
    public boolean isSDNotifyEnabled() {
        return sdNotifyEnabled;
    }

    /**
     * In ms, the time without watchdog updates before the watchdog kills the server. 0 if watchdog is disabled.
     */
    public long getWatchdogInterval() {
        return watchdogInterval;
    }

    /**
     * In ms, the interval we try to send watchdog and status updates to the service manager with {@code sd_notify(3)}.
     */
    public long getNotifyInterval() {
        return notifyInterval;
    }

    /**
     * Obtain a status message describing the current state of the server from the {@link StatusProvider} service.
     *
     * @return empty if no {@link StatusProvider} service is registered or threw an exception
     */
    public Optional<String> buildStatus() {
        Optional<String> status = Optional.empty();

        StatusProvider statusProvider = getServer().getServicesManager().load(StatusProvider.class);
        if (statusProvider != null) {
            try {
                status = Optional.of(statusProvider.status());
            } catch (RuntimeException e) {
                // Failsafe in case a custom StatusProvider misbehaves (which would make us not send notifications!)
                getLogger().log(Level.SEVERE, "StatusProvider threw an exception, ignoring", e);
            }
        }

        return status;
    }
}
