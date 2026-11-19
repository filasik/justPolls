# Installation

## Requirements

| | |
| :--- | :--- |
| Server | **Paper** (JustPolls is built against Minecraft `26.3`). Forks such as Purpur work too. Folia is supported by design. |
| Java | **25** or newer |
| Clients | Java Edition with dialog support (Minecraft 1.21.6 or newer). Nothing to install on the client. |
| Internet | Needed on the **first start** only (see below) |

> Spigot and plain Bukkit are **not** supported: JustPolls is a Paper plugin (`paper-plugin.yml`) and uses Paper's dialog, Brigadier command and scheduler APIs.

## Steps

1. Download `JustPolls-<version>.jar` from [Modrinth](https://modrinth.com/plugin/oi0pQjlz), Hangar or [GitHub Releases](https://github.com/filasik/justPolls/releases).
2. Put it into the `plugins/` folder of your server.
3. Start the server.
4. Check the console. A successful start ends with the JustPolls banner showing the storage, language and number of open polls.
5. Optionally edit `plugins/JustPolls/config.yml` (see [Configuration](Configuration)) and run `/justpolls reload`.
6. Create your first poll: `/poll quick 10m Best season? | Summer | Winter`.

## First start: library download

To keep the jar small, the database drivers are **not** bundled. On the first start Paper downloads them from Maven Central and caches them in the server's `libraries/` folder:

- `org.xerial:sqlite-jdbc`
- `org.mariadb.jdbc:mariadb-java-client`
- `com.zaxxer:HikariCP`

After that the server can start offline. If your host blocks outgoing connections, allow Maven Central once or copy the `libraries/` folder from another server.

## Files created

```
plugins/JustPolls/
├── config.yml            # all options, commented (see Configuration)
├── messages_en.yml       # English texts (MiniMessage)
├── messages_cs.yml       # Czech texts
├── justpolls.db          # SQLite database (only with storage.type: sqlite)
└── config.yml.bak-v<n>   # backup made before an automatic config update
```

## Updating

1. Replace the jar in `plugins/` and restart.
2. New options are **added to your existing `config.yml` automatically** with their comments; your values stay. A backup `config.yml.bak-v<n>` is written first.
3. Missing message keys fall back to the built-in text, so your edited message files keep working.
4. The database schema is migrated automatically.

Players with `justpolls.admin` are told on join when a newer release exists on GitHub (switch this off with `update-check: false`).

## Uninstalling

Remove the jar. `plugins/JustPolls/` holds the config, messages and (with SQLite) the database; delete it if you do not want to keep the poll history. With MariaDB the tables are named `justpolls_*`.
