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

### Usage Example

You can query the server using standard tools like `qstat`.

#### Command
```bash
qstat -a2s 127.0.0.1:29415 -json -R -P
```

#### Response
```json
[
	{
		"protocol": "a2s",
		"address": "127.0.0.1:29415",
		"status": "online",
		"hostname": "127.0.0.1:29415",
		"name": "Hytale Server",
		"gametype": "hytale",
		"map": "world",
		"numplayers": 1,
		"maxplayers": 4,
		"numspectators": 0,
		"maxspectators": 0,
		"ping": 26,
		"retries": 0,
		"rules": {
			"protocol": "11",
			"gamedir": "hytale",
			"gamename": "Hytale",
			"bots": "0",
			"dedicated": "1",
			"sv_os": "linux",
			"password": "1",
			"version": "2026.01.13-50e69c385",
			"game_port": "29400",
			"hostport": "29400",
			"protocol_version": "1",
            "plugins_enabled": "82",
			"tps_default": "30",
			"auth_status": "authenticated",
			"patchline": "release",
			"revision": "50e69c385653343d6bf1ad0103333f96dd93f54b",
			"protocol_hash": "6708f121966c1c443f4b0eb525b2f81d0a8dc61f5003a692a8fa157e5e02cea9"
		},
        "players": [
            {
                "name": "Tyrola",
                "score": 0,
                "time": "10m11s"
            }
        ]
    }
]
```
