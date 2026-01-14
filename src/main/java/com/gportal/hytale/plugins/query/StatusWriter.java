package com.gportal.hytale.plugins.query;

import com.gportal.a2s.PlayerInfo;
import com.gportal.a2s.QueryServer;
import com.gportal.a2s.ServerInfo;

import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;

import java.net.InetSocketAddress;
import java.util.Objects;

public class StatusWriter {
    private QueryServer server;

    public void start() {
        var ip = resolveQueryHost();
        var port = resolveQueryPort();
        ServerInfo info = createServerInfo(ip);

        server = new QueryServer(new InetSocketAddress(ip, port), info);

        updateStatus();

        System.out.println("[QueryPlugin] A2S Server started on " + ip + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private ServerInfo createServerInfo(String ip) {
        var hytale = HytaleServer.get();

        return new ServerInfo(
                new InetSocketAddress(ip, 28001),
                (byte) 17,
                hytale.getServerName(),
                Universe.get() != null && Universe.get().getDefaultWorld() != null ? Universe.get().getDefaultWorld().getName() : "world",
                "hytale",
                "Hytale",
                (short) 0,
                (byte) (Universe.get() != null ? Universe.get().getPlayerCount() : 0),
                (byte) hytale.getConfig().getMaxPlayers(),
                (byte) 0,
                'd',
                'o',
                hytale.getConfig().getPassword() != null && !Objects.equals(hytale.getConfig().getPassword(), ""),
                false,
                ManifestUtil.getImplementationVersion(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public void updateStatus() {
        if (server == null) return;

        var universe = Universe.get();
        if (universe == null) return;

        // Update server info
        server.info.setPlayers((byte) (universe.getPlayerCount()));

        // Update players
        server.players.clear();
        byte id = 0;
        for (var entry : universe.getWorlds().entrySet()) {
            var world = entry.getValue();
            for (var ref : world.getPlayerRefs()) {
                server.players.add(new PlayerInfo(id++, ref.getUsername(), (short) 0, 0.0f));
            }
        }

        // Update rules
        server.rules.put("version", ManifestUtil.getImplementationVersion());
        server.rules.put("patchline", ManifestUtil.getPatchline());
        server.rules.put("revision", ManifestUtil.getImplementationRevisionId());
        server.rules.put("protocol_version", String.valueOf(ProtocolSettings.PROTOCOL_VERSION));
        server.rules.put("protocol_hash", ProtocolSettings.PROTOCOL_HASH);
    }

    private String resolveQueryHost() {
        String env = System.getenv("QUERY_HOST");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        if (!Options.getOptionSet().has(Options.BIND)) {
            return "0.0.0.0";
        }

        try {
            return Options.getOptionSet().valuesOf(Options.BIND).getFirst().getAddress().getHostAddress();
        } catch (Exception e) {
            System.out.println("[QueryPlugin] Failed to resolve QUERY_HOST: " + e.getMessage());
            return "0.0.0.0";
        }
    }

    private int resolveQueryPort() {
        String env = System.getenv("QUERY_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException e) {
                System.out.println("[QueryPlugin] Invalid QUERY_PORT: " + env);
            }
        }

        return Options.getOptionSet().valuesOf(Options.BIND).getFirst().getPort() + 1;
    }
}
