# JustPolls

Timed in-game polls with a clickable dialog menu, SQLite/MariaDB storage and Discord webhook results.

Staff create a poll (10 minutes, an hour, a week, ...) in a menu or from `config.yml`. Players get the voting menu
popping up by themselves, click an answer and that is it: **one vote per player and poll**. When time is up the
results (percentages and totals, never who voted) are announced in chat and, if you want, posted to a Discord channel.

The menus use the native dialog screens introduced in Minecraft 1.21.6 (buttons, text fields, drop-downs, checkboxes),
so there is no inventory GUI and nothing for players to install.

## Features
- **Menu-driven**: create, manage, extend, end or cancel polls in dialogs (`/justpolls create`, `/justpolls manage`)
- **Any duration**: `30s`, `10m`, `1h`, `1d12h`, `7d`, `2w`; polls can also be scheduled to start later
- **Auto-open**: the voting menu opens by itself after a poll starts, when players join, and optionally as a reminder;
  every delay is configurable, players can opt out with `/justpolls autoopen off`
- **One vote per player** (optionally changeable). Votes are stored under a salted hash, not next to the player's UUID
- **Discord webhook**: nicely formatted embed with a bar, percentage and vote count per option, the winner (or tie),
  total votes and duration; optional role mention and a "poll started" message
- **SQLite or MariaDB/MySQL**, all database work is asynchronous; several servers can share one database
- **Polls in `config.yml`**: fixed polls that start at a date and even repeat (weekly event vote, ...)
- Results visibility: after voting, always live, or only after the poll ends
- Optional participation rewards (console commands run when a player votes)
- Languages: English (default) and Czech, every text is editable (MiniMessage)
- Folia-ready by design (no `BukkitScheduler`, everything goes through region/global/async schedulers); developed and tested on Paper

## Installation
1. Download the `.jar` from Modrinth / GitHub Releases.
2. Put it into `plugins/` on a Paper server (Minecraft 26.3).
3. Start the server (the first start needs internet access: database drivers are downloaded once from Maven Central).

## Commands and permissions
Base command `/justpolls`, aliases `/poll` and `/polls` (configurable).

| Command | Permission | Description |
|---|---|---|
| `/justpolls` | `justpolls.use` (everyone) | Open the voting menu (or a list when several polls run) |
| `/justpolls vote [id]` | `justpolls.vote` (everyone) | Open a poll |
| `/justpolls list` / `history` / `results <id>` | `justpolls.use` | Running polls, finished polls, results |
| `/justpolls autoopen [on\|off]` | `justpolls.use` | Turn the automatic menu on or off for yourself |
| `/justpolls create` | `justpolls.create` (OP) | Create a poll in a menu |
| `/justpolls quick <duration> <question> \| <option> \| <option> ...` | `justpolls.create` (OP) | Create a poll from one line, e.g. `/poll quick 1h Best season? \| Summer \| Winter` |
| `/justpolls manage` | `justpolls.admin` (OP) | Manage polls in a menu |
| `/justpolls end\|cancel\|delete <id>` | `justpolls.admin` | End now (publishes results), cancel (no results), delete with votes |
| `/justpolls extend <id> <duration>` | `justpolls.admin` | Move the end, e.g. `+1h` or `-30m` |
| `/justpolls open <id>` | `justpolls.admin` | Pop the menu up for every online player who has not voted |
| `/justpolls webhook <id>` / `webhook-test` | `justpolls.admin` | Send results again / test the webhook |
| `/justpolls reload` | `justpolls.admin` | Reload config.yml and messages |

## Discord results
1. In Discord: Channel settings > Integrations > Webhooks > New Webhook > Copy Webhook URL.
2. In `config.yml`: `discord.enabled: true` and paste the URL into `discord.webhook-url`.
3. `/justpolls webhook-test`, then create a poll. Each poll has a "send to Discord" switch (default
   `discord.send-by-default`). If Discord is down when a poll ends, the message stays queued and is retried on the next start
   or with `/justpolls webhook <id>`.

Only the webhook URL of Discord itself is accepted. The message never contains player names.

## Configuration
See `plugins/JustPolls/config.yml` (every option is commented). When you update the plugin, new options are added to your
existing file automatically; your values stay and a backup `config.yml.bak-v<n>` is saved first.
Messages live in `plugins/JustPolls/messages_<locale>.yml` (MiniMessage); set `locale` in config.yml.

## Privacy
Votes are stored as `SHA-256(salt : poll : player)`. A database dump does not show who voted for what, and the plugin
has no command or screen that does. (The salt is stored in the same database, so this is privacy by design,
not protection against someone with full database access and a list of player UUIDs.)

## Build
```
./gradlew build        # compiles (warnings are errors), runs tests, jar in build/libs
./gradlew runServer    # local test Paper server (-PrunDir=<path> reuses an existing server directory)
./scripts/smoke-test.sh
```

## Support the project
The plugin is free. If you like it, you can contribute voluntarily (no in-game perks): see the Sponsor links on GitHub.

## License
MIT
