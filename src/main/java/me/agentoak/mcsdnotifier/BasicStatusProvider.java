package me.agentoak.mcsdnotifier;

import org.bukkit.Server;

/**
 * StatusProvider implementation that just contains some basic information: server name, version, player count, plugin
 * count and world count.
 */
public class BasicStatusProvider implements StatusProvider {
    private final Server server;

    public BasicStatusProvider(Server server) {
        this.server = server;
    }

    @Override
    public String status() {
        return String.format("Running %s %s with %d/%d players, %d plugins, %d worlds", server.getName(),
            server.getVersion(), server.getOnlinePlayers().size(), server.getMaxPlayers(),
            server.getPluginManager().getPlugins().length, server.getWorlds().size());
    }
}
