package me.agentoak.mcsdnotifier;

import org.bukkit.Server;

/**
 * {@link StatusProvider} implementation that contains server name, version, player count and TPS averages.
 * <p>
 * Requires {@link Server} with a {@code #getTPS()} method, which is available in Paper API.
 *
 * @see #isSupported()
 */
public class TPSStatusProvider implements StatusProvider {
    private final Server server;

    public TPSStatusProvider(Server server) {
        if (!isSupported()) {
            throw new IllegalStateException("Server is missing Server#getTPS() method");
        }
        this.server = server;
    }

    @Override
    public String status() {
        return String.format("Running %s %s with %d/%d players, TPS avg: %s", server.getName(),
            server.getVersion(), server.getOnlinePlayers().size(), server.getMaxPlayers(), buildTPSString());
    }

    protected String buildTPSString() {
        double[] tps = (double[]) ReflectionUtils.methodGetter(server, "getTPS");
        StringBuilder sb = new StringBuilder(tps.length * 6);
        for (double t : tps) {
            sb.append(String.format(" %.2f", t));
        }
        return sb.substring(1);
    }

    /**
     * Checks if {@link Server} has a {@code #getTPS()} method which is required for this {@code StatusProvider}.
     */
    public static boolean isSupported() {
        return ReflectionUtils.methodExists(Server.class, "getTPS");
    }
}
