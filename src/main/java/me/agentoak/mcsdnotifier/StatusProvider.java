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
     * Generate a single-line free-form string to pass to a service manager that describes the current service state.
     * <p>
     * Implementations may not assume the server to be in any specific state. This method should not fail even if called
     * during server startup or shutdown!
     *
     * @return May not be {@code null}.
     */
    String status();
}
