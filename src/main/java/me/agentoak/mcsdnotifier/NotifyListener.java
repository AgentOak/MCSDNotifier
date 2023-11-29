package me.agentoak.mcsdnotifier;

import me.agentoak.mcsdnotifier.system.SDNotify;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

final class NotifyListener implements Listener {
    private static final long NSEC_PER_MSEC = 1_000_000L;

    private final MCSDNotifierPlugin plugin;
    private final SDNotify sdNotify;

    private long nextNotifyTime;
    private boolean seenDisableAll;
    private boolean takedown;

    NotifyListener(MCSDNotifierPlugin plugin, SDNotify sdNotify) {
        this.plugin = plugin;
        this.sdNotify = sdNotify;

        // Get a notification out on the first tick
        nextNotifyTime = 0;
    }

    private static long monotonicMillis() {
        // System.currentTimeMillis() is not monotonic
        return System.nanoTime() / NSEC_PER_MSEC;
    }

    /**
     * @return whether server is in the process of taking everything down (i.e. reload or shutdown).
     */
    boolean isTakedown() {
        return takedown;
    }

    /*
     * Just scheduling a timer to fire right at nextNotifyTime is not good enough; for the watchdog updates to
     * have any meaning, we need to make them block on the main thread. However, the server running slowly (i.e.
     * tick time increases) should not trigger the watchdog, as long as server is responsive at all. So check if
     * we should update watchdog in every tick.
     */
    public void onTick() {
        long currentTime = monotonicMillis();
        if (currentTime >= nextNotifyTime) {
            if (nextNotifyTime == 0) {
                // This is the first tick, so signal that startup/reload is finished
                plugin.getLogger().info("Server ready - notifying service manager");
                sdNotify.ready(plugin.buildStatus().orElse(null));
            } else {
                sdNotify.watchdog(plugin.buildStatus().orElse(null));
            }

            nextNotifyTime = currentTime + plugin.getNotifyInterval();
        }
    }

    /*
     * None of Bukkit/Spigot/Paper have events for reload/shutdown!? But in either case PluginManager#disablePlugins
     * gets called at some point, so this event seems to be the earliest possible moment to detect a reload/shutdown.
     * PluginDisableEvent is fired before the plugin gets disabled so even if this PluginDisableEvent is for our plugin,
     * we still get it.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginDisable(PluginDisableEvent event) {
        // We never reset seenDisableAll, but onEnable creates a new NotifyListener instance anyway
        if (seenDisableAll) {
            // Skip checks if we already know it's a reload/stop (or couldn't determine it the first time)
            return;
        }

        // isStopping is Paper-specific 1.15.2+ API
        if (ReflectionUtils.methodIsTrue(plugin.getServer(), "isStopping")) {
            handleTakedown(false);
            return;
        }

        // This is unreliable, but we only use it for the STOPPING=1 and RELOADING=1 signals which are not as important
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            // stop/reload method would be further down in stack than PluginManager#disablePlugins
            if (seenDisableAll) {
                /*
                 * Don't check class names because they could be different in each Bukkit/Spigot API implementation.
                 * This is especially unreliable because if the shutdown is caused by Minecraft itself (instead of
                 * through Bukkit/Spigot API), all method names indicative of a stop are obfuscated. Oh well.
                 */
                if ("stopServer".equals(e.getMethodName()) || "safeShutdown".equals(e.getMethodName()) ||
                        "shutdown".equals(e.getMethodName()) || "halt".equals(e.getMethodName()) ||
                        "close".equals(e.getMethodName()) || "restart".equals(e.getMethodName())) {
                    handleTakedown(false);
                    return;
                } else if ("reload".equals(e.getMethodName())) {
                    handleTakedown(true);
                    return;
                }
            } else if ("disablePlugins".equals(e.getMethodName()) || "clearPlugins".equals(e.getMethodName())) {
                // This is not an individual plugin disable (which we wouldn't care about for sd_notify)
                seenDisableAll = true;
            }
        }

        if (seenDisableAll) {
            /*
             * The last notify time could be up to MCSDNotifierPlugin#MAX_NOTIFY_INTERVAL ago. In case this is an
             * undetected stop/reload, we update the watchdog one last time, so we don't get killed too early during
             * shutdown. Makes WatchdogSec timeout consistent for shutdown on servers lacking Server#isStopping
             */
            sdNotify.watchdog(plugin.buildStatus().orElse(null));
        }
    }

    private void handleTakedown(boolean reload) {
        if (!takedown) {
            seenDisableAll = true;
            takedown = true;
            plugin.getLogger().info("Detected " + (reload ? "reload" : "stop") + " - notifying service manager");
            if (reload) {
                sdNotify.reloading(null);
            } else {
                sdNotify.stopping(null);
            }
        }
    }
}
