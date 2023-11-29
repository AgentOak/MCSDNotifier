package me.agentoak.mcsdnotifier;

/**
 * Service that generates single-line free-form status strings that describe the state of this server.
 * <p>
 * To have MCSDNotifier use this {@code StatusProvider}, just register it in the
 * {@link org.bukkit.plugin.ServicesManager} obtained by calling {@link org.bukkit.Server#getServicesManager()} with a
 * priority higher than {@link org.bukkit.plugin.ServicePriority#Lowest}.
 */
@FunctionalInterface
public interface StatusProvider {
    /**
     * Generate a single-line free-form string to pass to the service manager that describes the current service state.
     * <p>
     * Will be called during normal server operation, i.e. after all plugins have been enabled once ticks
     * start happening and before any plugins are disabled when shutting down or reloading.
     *
     * @return May not be {@code null}.
     */
    String status();
}
