package com.gportal.hytale.plugins.query;

import com.gportal.a2s.PlayerInfo;
import com.gportal.a2s.QueryServer;
import com.gportal.a2s.ServerInfo;

import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.io.PacketHandler;
import io.netty.util.AttributeKey;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Objects;

public class StatusWriter {
    private static AttributeKey<Long> LOGIN_START_KEY;

    static {
        try {
            Field field = PacketHandler.class.getDeclaredField("LOGIN_START_ATTRIBUTE_KEY");
            field.setAccessible(true);
            LOGIN_START_KEY = (AttributeKey<Long>) field.get(null);
        } catch (Exception e) {
            System.out.println("[QueryPlugin] Failed to access LOGIN_START_ATTRIBUTE_KEY: " + e.getMessage());
        }
    }

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

    // https://developer.valvesoftware.com/wiki/Server_queries
    private ServerInfo createServerInfo(String ip) {
        var hytale = HytaleServer.get();

        char environment = System.getProperty("os.name").toLowerCase().contains("win") ? 'w' : 'l';

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
                environment,
                hytale.getConfig().getPassword() != null && !Objects.equals(hytale.getConfig().getPassword(), ""),
                false,
                ManifestUtil.getImplementationVersion(),
                resolveGamePort(),
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
                server.players.add(new PlayerInfo(id++, ref.getUsername(), (short) 0, getPlayerConnectionTime(ref)));
            }
        }

        // Update rules
        server.rules.put("patchline", ManifestUtil.getPatchline());
        server.rules.put("revision", ManifestUtil.getImplementationRevisionId());
        server.rules.put("protocol_version", String.valueOf(ProtocolSettings.PROTOCOL_VERSION));
        server.rules.put("protocol_hash", ProtocolSettings.PROTOCOL_HASH);
        server.rules.put("auth_status",  getAuthStatus());
        server.rules.put("max_view_radius",  String.valueOf(HytaleServer.get().getConfig().getMaxViewRadius()));

        long enabledPlugins = PluginManager.get().getPlugins().stream().filter(PluginBase::isEnabled).count();
        server.rules.put("plugins_enabled", String.valueOf(enabledPlugins));

        universe.getWorlds().forEach((name, world) -> server.rules.put("tps_" + name, String.valueOf(world.getTps())));
    }

    private float getPlayerConnectionTime(PlayerRef ref) {
        if (LOGIN_START_KEY == null || ref == null) return 0.0f;

        try {
            var channel = ref.getPacketHandler().getChannel();
            Long startNano = channel.attr(LOGIN_START_KEY).get();
            if (startNano == null) return 0.0f;

            long elapsedNano = System.nanoTime() - startNano;
            return (float) elapsedNano / 1_000_000_000.0f;
        } catch (Exception e) {
            return 0.0f;
        }
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

    private short resolveGamePort() {
        if (!Options.getOptionSet().has(Options.BIND)) {
            return (short) 0;
        }

        return (short) Options.getOptionSet().valuesOf(Options.BIND).getFirst().getPort();
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

    private String getAuthStatus() {
        ServerAuthManager manager = ServerAuthManager.getInstance();

        if (manager.hasSessionToken() && manager.hasIdentityToken()) {

            Instant tokenExpiry = manager.getTokenExpiry();
            if (tokenExpiry != null) {
                long secondsRemaining = tokenExpiry.getEpochSecond() - Instant.now().getEpochSecond();
                if (secondsRemaining <= 0) {
                    return "expired";
                }
            }


            return "authenticated";
        } else if (manager.hasSessionToken() || manager.hasIdentityToken()) {
            return "partial";
        }

        return "unauthenticated";
    }
}
