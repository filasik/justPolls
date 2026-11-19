# Commands and Permissions

The base command is `/justpolls` with the aliases `/poll` and `/polls` (change them in `command.aliases`, restart required). Tab completion suggests the ids of running and scheduled polls.

## Player commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/justpolls` | `justpolls.use` | Open the voting menu of the running poll, or a list when several polls run. From the console it prints the help. |
| `/justpolls vote [id]` | `justpolls.vote` | Open a poll. Without an id it behaves like `/justpolls`. A finished poll opens its results. |
| `/justpolls list` | `justpolls.use` | Running and scheduled polls you can vote in, with clickable **[vote]** / **[results]** links. |
| `/justpolls history` | `justpolls.use` | Latest finished polls (a menu for players, chat lines for the console). |
| `/justpolls results <id>` | `justpolls.use` | Results of a poll in chat (subject to `voting.show-results` while it runs). |
| `/justpolls autoopen [on\|off]` | `justpolls.use` | Turn the automatic voting menu on or off for yourself. Without an argument it toggles. |

## Staff commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/justpolls create` | `justpolls.create` | Create a poll in a menu. |
| `/justpolls quick <duration> <question> \| <option> \| <option> ...` | `justpolls.create` | Create a poll from one line. Works from the console too. |
| `/justpolls manage` | `justpolls.admin` | Manage polls in a menu. |
| `/justpolls end <id>` | `justpolls.admin` | End a running poll now and publish its results. |
| `/justpolls cancel <id>` | `justpolls.admin` | Cancel a running or scheduled poll. Votes are kept, no results are published. |
| `/justpolls delete <id>` | `justpolls.admin` | Delete a poll with all its votes. Cannot be undone. |
| `/justpolls extend <id> <duration>` | `justpolls.admin` | Move the end: `+1h` or `1h` later, `-30m` earlier. Refused if the new end would be in the past. |
| `/justpolls open <id>` | `justpolls.admin` | Pop the voting menu up for every online player who may vote and has not voted yet. |
| `/justpolls webhook <id>` | `justpolls.admin` | Send the results of a finished poll to Discord (again). |
| `/justpolls webhook-test` | `justpolls.admin` | Send a test message to the configured Discord webhook. |
| `/justpolls reload` | `justpolls.admin` | Reload `config.yml` and the message files. Storage and aliases need a restart. |

Examples:

```
/poll quick 1h Best season? | Summer | Winter | Spring | Autumn
/poll quick 1d12h Next event? | Build battle | Parkour race
/poll extend 4 +2h
/poll extend 4 -30m
/poll end 4
```

## Permissions

| Permission | Default | Grants |
| :--- | :--- | :--- |
| `justpolls.use` | everyone | The base command, `list`, `history`, `results`, `autoopen`. |
| `justpolls.vote` | everyone | Voting in polls. |
| `justpolls.create` | op | Creating polls (menu and `quick`). |
| `justpolls.admin` | op | Managing every poll, `reload`, Discord commands, update notifications on join. Admins also always see live results. |

### Per-poll permission

A poll defined in `config.yml` can carry its own `permission` (for example `group.vip`). Only players who have it **and** `justpolls.vote` see the poll and can vote in it. See [Scheduled and Repeating Polls](Scheduled-and-Repeating-Polls).

### Examples with LuckPerms

```
# stop guests from voting
/lp group default permission set justpolls.vote false

# let moderators create polls but not manage them
/lp group moderator permission set justpolls.create true
```
