# Configuration

Everything lives in `plugins/JustPolls/config.yml`; every option is commented in the file itself.

- `/justpolls reload` applies most changes. **Storage settings and command aliases need a restart.**
- Every value is validated. An invalid value is replaced by its default and a warning is logged; the plugin never refuses to start over a typo.
- When you update the plugin, new options are added to your file automatically (see [Installation](Installation#updating)). Do not change `config-version`.

## Durations

Wherever the config (or a command) takes a duration, use a number with a unit: `s`, `m`, `h`, `d`, `w`. Units can be combined (`1h30m`, `1d12h`). A bare number means **minutes**. In the `auto-open` section `0` means "off".

Examples: `30s`, `10m`, `1h`, `1d12h`, `7d`, `2w`.

## General

| Option | Default | Description |
| :--- | :--- | :--- |
| `locale` | `en` | Language of all player-facing messages: `en` or `cs`. See [Messages and Localization](Messages-and-Localization). |
| `update-check` | `true` | Check GitHub releases for a newer version (at startup, then every 12 hours). Players with `justpolls.admin` are told on join. |
| `command.aliases` | `[poll, polls]` | Extra aliases for `/justpolls`. Restart required. Remove an alias if another plugin already owns it. The first alias is the one shown in help texts and clickable chat links. |
| `metrics` | `true` | Anonymous [bStats](https://bstats.org/) statistics (server count, Minecraft version, language, storage type). The global switch is `plugins/bStats/config.yml`. |

## `voting`

| Option | Default | Description |
| :--- | :--- | :--- |
| `allow-change` | `false` | `false`: a vote is final. `true`: a player may move their vote to another option while the poll runs (still one vote per player). |
| `show-results` | `after-vote` | When players see the numbers: `after-vote` (right after they voted, and always after the end), `live` (whenever they open the poll), `after-end` (only when the poll is over). Players with `justpolls.admin` always see results. |
| `reward-commands` | `[]` | Console commands run when a player casts a vote (not when changing it). `%player%` is replaced by the player name. |

```yaml
voting:
  reward-commands:
    - "give %player% minecraft:diamond 1"
```

> Rewards for taking part are fine; never sell votes or advantages for money (Minecraft EULA).

## `creation`

Limits for polls created by staff (menu and `/justpolls quick`).

| Option | Default | Description |
| :--- | :--- | :--- |
| `min-duration` | `1m` | Shortest allowed poll. |
| `max-duration` | `30d` | Longest allowed poll. |
| `max-options` | `6` | Number of option fields in the creation menu (2–10). |
| `max-active-polls` | `5` | How many polls may run or be scheduled at the same time (1–100). Creating another one is refused until one ends. Polls from the `polls` section are never blocked by this limit. |
| `duration-presets` | `[10m, 30m, 1h, 6h, 1d, 3d, 7d]` | Choices in the duration drop-down of the creation menu. "Custom..." is always added. Presets outside min/max are ignored. |
| `default-duration` | `1h` | Preselected preset (must be one of `duration-presets`, otherwise the first preset is used). |

Fixed text limits (not configurable): question 100 characters, description 500, option 60, at most 10 options.

## `auto-open`

Opens the voting menu by itself for players who have not voted yet. See [Player Guide](Player-Guide#the-menu-opens-by-itself).

| Option | Default | Description |
| :--- | :--- | :--- |
| `enabled` | `true` | Master switch. Each poll also has its own auto-open switch. |
| `on-start-delay` | `5s` | Delay after a poll starts before the menu pops up for online players (`0` = immediately). A config poll can override it with `open-delay`. |
| `on-join-delay` | `15s` | Delay after a player joins before the menu pops up if a poll waits for their vote (`0` = never on join). |
| `reminder-interval` | `0` | Show the menu again this often to players who still have not voted (`0` = no reminders). |
| `max-opens` | `3` | The menu opens at most this many times per player and poll (start, join and reminders together; 1–50). |

## `announce`

| Option | Default | Description |
| :--- | :--- | :--- |
| `chat` | `true` | Chat message with a clickable **[click to vote]** button when a poll starts, and a summary with the winner and every option when it ends. |
| `sound` | `block.note_block.pling` | Minecraft sound key played with the start announcement. Empty = silent. |

## `discord`

See [Discord Webhook](Discord-Webhook) for setup and what the messages look like.

| Option | Default | Description |
| :--- | :--- | :--- |
| `enabled` | `false` | Send results to Discord when a poll ends. |
| `webhook-url` | `""` | The webhook URL. Only real Discord webhook URLs are accepted; anything else switches Discord off with a warning. |
| `username` | `JustPolls` | Author name of the messages. |
| `avatar-url` | `""` | Author picture (a public image URL). |
| `mention-role-id` | `""` | Numeric id of a role to ping with the results. |
| `color` | `#5865F2` | Embed colour (hex). |
| `announce-start` | `false` | Also post a message when a poll starts. |
| `bar-width` | `20` | Length of the bars in the results message (5–30). |
| `send-by-default` | `true` | Default of the "send to Discord" switch for new polls. |
| `server-name` | `""` | Shown in the message footer. |

## `polls`

Polls defined in the config, created once and optionally repeating. See [Scheduled and Repeating Polls](Scheduled-and-Repeating-Polls).

## `storage`

See [Storage and Database](Storage-and-Database).

| Option | Default | Description |
| :--- | :--- | :--- |
| `type` | `sqlite` | `sqlite` (single file, zero setup) or `mariadb` (MariaDB or MySQL; `mysql` is accepted as an alias). Restart required. |
| `sqlite.file` | `justpolls.db` | File name inside `plugins/JustPolls/`. |
| `sqlite.pool-size` | `3` | Connections (1–32). |
| `mariadb.host` / `port` / `database` / `user` / `password` | `localhost` / `3306` / `justpolls` / `justpolls` / `change-me` | Connection details. |
| `mariadb.pool-size` | `6` | Connections (1–64). |
| `mariadb.ssl-mode` | `""` | TLS mode: empty (driver default), `trust`, `disable`, `verify-ca`, `verify-full`. |
| `sync-interval-seconds` | `30` | How often running polls are re-read from the database (5–3600). Matters when several servers share one database. |
