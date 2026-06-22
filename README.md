# L-BedWarsProxy

Multi-server proxy plugin for L-BedWars — arena status tracking, player routing, queue system, leaderboard holograms, and cross-server spectate.

Runs on **Paper/Spigot 1.19+** (not a BungeeCord plugin) and communicates with game servers via TCP socket with HMAC-authenticated JSON protocol. Uses BungeeCord plugin channels for cross-server player movement.

## Features

### Arena Status Tracking
- Real-time arena state monitoring via TCP socket connections from game servers
- HMAC-SHA256 signed JSON protocol with configurable authentication token
- IP whitelist with CIDR support for connection security
- Per-IP connection limits with configurable max connections
- Health-check timeout system — automatically marks stale servers as offline
- Protocol version handshake for forward/backward compatibility

### Player Routing (`/bw join <mode>`)
- Smart arena selection algorithm: prefers WAITING lobbies with more players, falls back to STARTING lobbies
- Party-aware join — if player is party leader, all party members are sent to the same server simultaneously
- Rejoin support (`/bw rejoin`) — remembers player's last server for reconnection
- `/bw stats [player]` — view MySQL-stored stats across all servers

### Queue System
- Optional auto-queue when no arena is available for a mode
- Priority permissions for skip-the-line access (configurable permission nodes)
- Automatic connection when an arena slot opens
- Queue position display for players

### Admin Commands (`/bwa`)
- **GUI** — Paginated arena overview with status-colored wool items; click to join
- **Teleport/Spectate** — `/bwa tp <player>` — cross-server spectate via BungeeCord Forward channel
- **Leaderboard** — Paginated GUI with player heads, sorted by any stat column
- **Hologram management** — `/bwa hologram set/remove/list` — in-game leaderboard hologram placement

### Hologram Leaderboard
- ArmorStand-based holograms display top N players for any stat
- Configurable line count and update interval
- Persists to `holograms.yml`
- Multiple holograms per stat supported
- Automatically updates from MySQL database on a timer

### MySQL Stats Database
- HikariCP connection pool for high-performance MySQL access
- Shared `player_stats` table with game servers (configurable prefix)
- Stats: kills, deaths, final kills, final deaths, wins, losses, beds broken, games played
- Leaderboard queries for any stat column
- Gracefully disabled when database section is omitted from config

### PlaceholderAPI Expansion
- `%lbedwars_kills%`, `%lbedwars_deaths%`, `%lbedwars_wins%`, `%lbedwars_losses%`
- `%lbedwars_final_kills%`, `%lbedwars_final_deaths%`
- `%lbedwars_beds_broken%`, `%lbedwars_games_played%`
- `%lbedwars_level%`, `%lbedwars_xp%`, `%lbedwars_kdr%`
- `%lbedwars_arena_online%` (total players across all servers)
- `%lbedwars_arena_count%` (total online arena servers)

### Party Integration
- Soft dependency on Parties API (alessiodp/parties)
- Party leader's `/bw join` sends all members to the same game server
- Graceful fallback when Parties plugin is not installed

### bStats
- Anonymous usage statistics with plugin ID `31956`
- Always enabled (no config toggle)

### Folia Support
- Reflection-based Folia detection
- Dual scheduler (Folia regionized / Bukkit) for all timed tasks
- `folia-supported: true` in plugin.yml


## Commands

### Player Commands

| Command | Description |
|---|---|
| `/bw join <solo/duo/trio/quad>` | Join a mode (auto-selects best arena, queues if full) |
| `/bw rejoin` | Reconnect to last game server |
| `/bw stats [player]` | View MySQL-stored stats |

### Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/bwa gui [all/solo/duo/trio/quad]` | `lbedwarsproxy.admin` | Open arena status GUI |
| `/bwa tp <player>` | `lbedwarsproxy.admin` | Cross-server spectate |
| `/bwa leaderboard [stat]` | `lbedwarsproxy.admin` | Open leaderboard GUI |
| `/bwa hologram set <stat>` | `lbedwarsproxy.admin` | Place leaderboard hologram |
| `/bwa hologram remove [id]` | `lbedwarsproxy.admin` | Remove a hologram |
| `/bwa hologram list` | `lbedwarsproxy.admin` | List all holograms |

### Queue Placeholders

| Placeholder | Description |
|---|---|
| `%lbedwars_arena_online%` | Total online players across all arenas |
| `%lbedwars_arena_count%` | Number of online arena servers |

## Configuration (`config.yml`)

```yaml
language: en

socket-port: 5000
bind-address: 127.0.0.1
socket-token: "change-me"
allowed-ips:
  - "127.0.0.1"
max-connections: 50

database:
  host: "localhost"
  port: 3306
  database: "lbedwars"
  username: "root"
  password: ""
  table-prefix: ""
  pool-size: 5

health-check:
  enabled: true
  timeout-seconds: 15

queue:
  enabled: false
  priority-permissions:
    - "lbedwarsproxy.queue.priority"

holograms:
  leaderboard:
    enabled: false
    lines: 10
    update-interval: 60
```

### Configuration Sections

| Section | Description |
|---|---|
| `language` | Language for proxy messages (`en` or `tr`) |
| `socket-port` | TCP port for game server connections |
| `bind-address` | Socket bind address (`0.0.0.0` for all interfaces) |
| `socket-token` | Shared secret for HMAC authentication |
| `allowed-ips` | CIDR whitelist (empty = allow all) |
| `max-connections` | Maximum concurrent socket connections |
| `database` | MySQL connection settings (omit to disable) |
| `health-check` | Timeout detection for stale servers |
| `queue` | Optional queue system settings |
| `holograms.leaderboard` | Leaderboard hologram refresh settings |




### Disconnect
- Client disconnect detected via socket timeout
- `TimeOutTask` marks server offline after `timeout-seconds` of no updates
- Per-IP connection counter decremented on disconnect

## Setup

### Prerequisites
- Paper/Spigot 1.19+ server (can be a dedicated proxy/lobby server)
- MySQL database (optional, required for stats/leaderboard)
- L-BedWars plugin on each game server with `proxy-mode: true`

### Installation
1. Place `L-BedWarsProxy.jar` in your proxy/lobby server's `plugins/` folder
2. Restart the server to generate default config
3. Edit `config.yml`:
   - Set `socket-token` to a secure random string
   - Configure `bind-address` and `socket-port` (match game server config)
   - Add game server IPs to `allowed-ips` (or leave empty to allow all)
   - Configure `database` section for MySQL (stats/leaderboard)
4. On each game server, set `proxy-mode: true`, `proxy-socket-address: <proxy-ip>:<port>`, and matching `socket-token`
5. Restart proxy server, then game servers

### Verifying Connection
- Game server console: `[L-BedWars] Socket connection established to proxy`
- Proxy console: `[L-BedWarsProxy] Server <name> connected!`
- `/bwa gui` should show the connected arena

## Dependencies

| Dependency | Type | Required |
|---|---|---|
| Paper/Spigot 1.19+ | Server | Yes |
| PlaceholderAPI | Plugin | No (placeholders unavailable) |
| Parties (alessiodp) | Plugin | No (party features disabled) |

## Files

| File | Purpose |
|---|---|
| `config.yml` | Main plugin configuration |
| `holograms.yml` | Persisted hologram data (auto-generated) |
| `languages/messages_en.yml` | English language file |
| `languages/messages_tr.yml` | Turkish language file |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `lbedwarsproxy.play` | `true` | Allows joining arenas via `/bw` |
| `lbedwarsproxy.admin` | `op` | Allows admin commands via `/bwa` |
| `lbedwarsproxy.queue.priority` | — | Queue priority (configurable node name) |
