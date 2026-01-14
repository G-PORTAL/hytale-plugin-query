# Hytale Query Plugin

A Hytale mod that exposes the Steam Query (A2S) protocol, allowing your server to be queried by standard tools and server lists.

### Installation

1. Download the latest `.jar` from the [releases page](https://github.com/G-PORTAL/hytale-plugin-query/releases).
2. Copy the `.jar` file to your Hytale server's `mods/` folder.
3. Restart the server.

### Configuration

By default, the plugin automatically detects the game server's listening address and port. It exposes the Steam Query (A2S) interface on **game port + 1**.

You can override these settings using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `QUERY_HOST` | The IP address to bind the query server to. | Game server bind address or `0.0.0.0` |
| `QUERY_PORT` | The port to listen for query requests on. | `Game Port + 1` |

### Features

- Supports A2S_INFO (Server information)
- Supports A2S_PLAYER (Player list)
- Supports A2S_RULES (Server rules/settings)
- Automatically updates player counts and world information.
