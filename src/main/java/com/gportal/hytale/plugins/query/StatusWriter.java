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
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.registry.Registration;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

public class StatusWriter {
    // Fallback bound for the session map when the configured max player count is unavailable
    // or unbounded (e.g. getMaxPlayers() returns <= 0).
    private static final int DEFAULT_PLAYER_CAPACITY = 256;

    private QueryServer server;

    // Tracks when each connected player's session started (System.nanoTime()) so we can report
    // per-player connection time — the Server 0.5.6 API no longer exposes it directly. Bounded
    // FIFO by the configured max player count: if a PlayerDisconnectEvent is ever missed, the
    // oldest entry is evicted rather than leaking. Synchronized because connect/disconnect events
    // fire off the status-update thread.
    private final Map<UUID, Long> sessionStartNanos = Collections.synchronizedMap(
            new BoundedSessionMap(StatusWriter::maxTrackedPlayers));

    private Registration connectListener;
    private Registration disconnectListener;

    public void start() {
        var ip = resolveQueryHost();
        var port = resolveQueryPort();
        ServerInfo info = createServerInfo(ip);

        server = new QueryServer(new InetSocketAddress(ip, port), info);

        registerConnectionTracking();

        updateStatus();

        System.out.println("[QueryPlugin] A2S Server started on " + ip + ":" + port);
    }

    public void stop() {
        if (connectListener != null) {
            connectListener.unregister();
            connectListener = null;
        }
        if (disconnectListener != null) {
            disconnectListener.unregister();
            disconnectListener = null;
        }
        sessionStartNanos.clear();

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
        server.rules.put("auth_status",  getAuthStatus());
        server.rules.put("max_view_radius",  String.valueOf(HytaleServer.get().getConfig().getMaxViewRadius()));

        long enabledPlugins = PluginManager.get().getPlugins().stream().filter(PluginBase::isEnabled).count();
        server.rules.put("plugins_enabled", String.valueOf(enabledPlugins));

        universe.getWorlds().forEach((name, world) -> server.rules.put("tps_" + name, String.valueOf(world.getTps())));
    }

    // Subscribe to player connect/disconnect events to track session start times. The Server 0.5.6
    // API no longer exposes connection time directly (the old PacketHandler.LOGIN_START_ATTRIBUTE_KEY
    // Netty channel attribute was removed when the transport moved to QUIC).
    private void registerConnectionTracking() {
        var eventBus = HytaleServer.get().getEventBus();

        connectListener = eventBus.register(PlayerConnectEvent.class, (PlayerConnectEvent event) -> {
            PlayerRef ref = event.getPlayerRef();
            if (ref != null) {
                // Overwrite any stale entry (e.g. a missed disconnect on a prior session).
                sessionStartNanos.put(ref.getUuid(), System.nanoTime());
            }
        });

        disconnectListener = eventBus.register(PlayerDisconnectEvent.class, (PlayerDisconnectEvent event) -> {
            PlayerRef ref = event.getPlayerRef();
            if (ref != null) {
                sessionStartNanos.remove(ref.getUuid());
            }
        });
    }

    // Seconds the player has been connected. Players already online before this plugin loaded (or
    // whose start time was evicted under the FIFO bound) are not tracked and report 0.
    private float getPlayerConnectionTime(PlayerRef ref) {
        if (ref == null) {
            return 0.0f;
        }

        Long startNano = sessionStartNanos.get(ref.getUuid());
        if (startNano == null) {
            return 0.0f;
        }

        long elapsedNano = System.nanoTime() - startNano;
        return (float) elapsedNano / 1_000_000_000.0f;
    }

    // Upper bound for the session map, tied to the server's configured max player count so it can
    // never exceed the number of players that can actually be online. Falls back to a safe default
    // if the config is not yet available or reports an unbounded (<= 0) value.
    private static int maxTrackedPlayers() {
        try {
            int maxPlayers = HytaleServer.get().getConfig().getMaxPlayers();
            if (maxPlayers > 0) {
                return maxPlayers;
            }
        } catch (Exception ignored) {
            // Server/config not ready — fall through to the default.
        }

        return DEFAULT_PLAYER_CAPACITY;
    }

    // FIFO-bounded map: evicts the oldest inserted entry once it grows past the capacity supplied
    // at eviction time. Guards against leaks if a disconnect event is ever missed.
    @SuppressWarnings("serial")
    private static final class BoundedSessionMap extends LinkedHashMap<UUID, Long> {
        private final IntSupplier capacity;

        private BoundedSessionMap(IntSupplier capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
            return size() > capacity.getAsInt();
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
