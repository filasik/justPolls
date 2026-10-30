# JustPolls

Timed in-game polls with a clickable dialog menu, SQLite/MariaDB storage and Discord webhook results.

![banner](https://raw.githubusercontent.com/filasik/justPolls/main/docs/assets/banner.png)

Run a vote for 10 minutes, an hour or a week. Players get a native click-to-vote menu, each player votes once, and when the time is up the results (percentages and totals, never who voted) go to chat and, optionally, to a Discord channel.

## Features
- **Native menus**: polls are created, voted in and managed in Minecraft's dialog screens (buttons, text fields, drop-downs, checkboxes). No inventory GUI, no client mod.
- **Any duration**: `30s`, `10m`, `1h`, `1d12h`, `7d`, `2w`. Polls can be scheduled to start later, extended or ended early.
- **Auto-open**: the voting menu opens by itself after a poll starts, when players join and (optionally) as a reminder. Delays are configurable per server and per poll; players can opt out with `/poll autoopen off`.
- **One vote per player and poll**, optionally changeable. Votes are stored under a salted hash, not next to the player's UUID.
- **Discord webhook**: formatted embed with a progress bar, percentage and vote count per option, the winner (or a tie), total votes and duration. Optional role ping and a "poll started" message. Handles Discord rate limits and retries undelivered results.
- **SQLite or MariaDB/MySQL**, fully asynchronous; several servers can share one database.
- **Polls in config.yml**: fixed polls with a start date that can even repeat (a weekly event vote).
- **Results visibility**: after voting, always live, or only after the poll ends.
- **Participation rewards**: optional console commands when a player votes.
- English and Czech, every text editable (MiniMessage).
- Config updates itself: new options are added to your file with comments, your values are never touched.

## Commands & Permissions
Base command `/justpolls`, aliases `/poll`, `/polls`.

| Command | Permission | Description |
|---|---|---|
| `/justpolls`, `vote [id]` | `justpolls.vote` (everyone) | Open the voting menu |
| `list`, `history`, `results <id>` | `justpolls.use` (everyone) | Running polls, finished polls, results |
| `autoopen [on\|off]` | `justpolls.use` (everyone) | Automatic menu on/off for yourself |
| `create` | `justpolls.create` (OP) | Create a poll in a menu |
| `quick <duration> <question> \| <option> \| <option>` | `justpolls.create` (OP) | Create a poll from one line |
| `manage` | `justpolls.admin` (OP) | Manage polls in a menu |
| `end`, `cancel`, `delete`, `extend`, `open`, `webhook <id>` | `justpolls.admin` (OP) | Staff actions |
| `webhook-test`, `reload` | `justpolls.admin` (OP) | Test Discord, reload config |

## Configuration
Everything is commented in `plugins/JustPolls/config.yml`:
- `voting`: allow changing a vote, when results are visible, rewards
- `creation`: min/max duration, number of options, duration presets
- `auto-open`: delays after start and join, reminders
- `discord`: webhook URL, name, colour, role ping, bar width
- `polls`: polls defined in the file (start date, repeat)
- `storage`: SQLite (default) or MariaDB

## Compatibility
- Paper (and forks such as Purpur); designed to be Folia-ready
- Minecraft 26.3 (the dialog menus need 1.21.6 or newer)
- Java 25
- The first start downloads the database drivers from Maven Central

## Support
Issues: https://github.com/filasik/justPolls/issues · Web: https://github.com/filasik/justPolls

This plugin is free. If you like it, you can support development (donations are voluntary and give no in-game advantages).
