package me.agentoak.mcsdnotifier;

import me.agentoak.mcsdnotifier.system.SDNotify;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;

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

        /*
         * Watchdog timer starts ticking from SDNotify#ready, but in Plugin#onEnable we don't know if server is just
         * starting (i.e. ServerLoadEvent STARTUP will occur) so we use our #onEnable time as fallback.
         */
        nextNotifyTime = monotonicMillis() + plugin.getNotifyInterval();
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        switch (event.getType()) {
            case STARTUP:
                plugin.getLogger().info("Server started, pid " + sdNotify.getPid() + " - notifying service manager");
                nextNotifyTime = monotonicMillis() + plugin.getNotifyInterval();
                sdNotify.ready(plugin.buildStatus().orElse(null));
                break;
            case RELOAD:
                plugin.getLogger().info("Server reloaded - notifying service manager");
                sdNotify.ready(plugin.buildStatus().orElse(null));
                break;
            default:
                plugin.getLogger().warning("Something is happening but we don't know what - not notifying service " +
                                               "manager. If server is killed by watchdog soon, this is the cause.");
                break;
        }
    }

    /*
     * Just scheduling a timer to fire right at nextNotifyTime is not good enough; for the watchdog updates to
     * have any meaning, we need to make them block on the main thread. However, the server running slowly (i.e.
     * tick time increases) should not trigger the watchdog, as long as server is responsive at all. So check if
     * we should update watchdog in every tick.
     *
     * Note that we (correctly) won't send watchdog updates before we send ready, because ticks only start happening
     * once the server is fully started.
     */
    public void onTick() {
        long currentTime = monotonicMillis();
        if (currentTime >= nextNotifyTime) {
            nextNotifyTime = currentTime + plugin.getNotifyInterval();
            sdNotify.watchdog(plugin.buildStatus().orElse(null));
        }
    }

    /*
     * Detecting commands seems even more fragile; we cannot verify if the command actually executed, because we only
     * get a preprocess event. It's possible the command does not even exist, or the permission is different. Some
     * forks (e.g. Paper) deliberately break the reload command and only work when passed "confirm" as argument. We
     * would also have to consider aliases and check if a plugin overwrote the command.
     */
    /*
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        switch (event.getCommand()) {
            case "spigot:restart":
            case "restart":
                if (!ReflectionUtils.classExists("org.spigotmc.RestartCommand") ||
                        !event.getSender().hasPermission("bukkit.command.restart")) {
                    break;
                }
                handleTakedown(false);
                break;
            case "minecraft:stop":
            case "stop":
                if (!event.getSender().hasPermission("minecraft.command.stop")) {
                    break;
                }
                handleTakedown(false);
                break;
            case "bukkit:reload":
            case "reload":
                if (!event.getSender().hasPermission("bukkit.command.reload")) {
                    break;
                }
                handleTakedown(true);
                break;
            default:
                break;
        }
    }
     */

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
