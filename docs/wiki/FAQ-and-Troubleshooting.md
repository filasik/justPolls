# FAQ and Troubleshooting

## FAQ

### Do players need a mod or resource pack?
No. The menus are Minecraft's own dialog screens (Java Edition 1.21.6 and newer).

### Does it work on Spigot?
No. JustPolls is a Paper plugin. It runs on Paper and its forks, and Folia is supported.

### Can players change their vote?
Only if `voting.allow-change: true`. They still have one vote per poll; clicking another option moves it. Rewards are given only for the first vote.

### Can I see who voted for what?
No, by design. See [Privacy](Privacy).

### Can players see results before the poll ends?
That is `voting.show-results`: `after-vote` (default), `live` or `after-end`. Staff with `justpolls.admin` always see them.

### How do I run the same poll every week?
Define it in the `polls` section with `repeat: 7d`. See [Scheduled and Repeating Polls](Scheduled-and-Repeating-Polls).

### Can I start a poll from the console or another plugin?
Yes: `justpolls quick 1h Question? | A | B` works from the console, so any scheduler or plugin that runs console commands can use it.

### Can I limit a poll to VIPs?
Yes, for polls in `config.yml`: add `permission: "group.vip"`. See [Scheduled and Repeating Polls](Scheduled-and-Repeating-Polls).

### The menu pops up too often / not at all.
Tune the `auto-open` section: `on-start-delay`, `on-join-delay`, `reminder-interval`, `max-opens`. Players can opt out with `/poll autoopen off`. Staff can force it once with `/poll open <id>`.

### Can I give rewards for voting?
Yes, `voting.reward-commands`. Do not sell votes or in-game advantages for money (Minecraft EULA).

### How do I disable the `/poll` alias?
Remove it from `command.aliases` and restart.

## Troubleshooting

### "JustPolls is still starting up"
The database is opened asynchronously on startup. Wait a moment. If it never goes away, check the console for a database error.

### `Cannot open the database: ...` and the plugin disables itself
Check the `storage` section. For MariaDB check host, port, user, password and that the database exists. Restart after changing storage settings.

### `Cannot use authentication plugin caching_sha2_password with a Self signed certificates`
MySQL 8+ with its default certificate. Set `storage.mariadb.ssl-mode: trust` (or `disable` on a trusted local network) and restart.

### The server fails on the first start with a download error
The database drivers are downloaded from Maven Central on the first start. Allow outgoing HTTPS for that start, or copy the `libraries/` folder from a server that has them.

### `discord.webhook-url is not a Discord webhook URL ...; Discord results are switched off`
The URL must be `https://discord.com/api/webhooks/<id>/<token>`. Copy it again from Discord (Channel settings → Integrations → Webhooks).

### `/poll webhook-test` fails
The console shows Discord's answer. Common causes: the webhook was deleted (HTTP 404), the token is wrong (401), or the host blocks outgoing connections. Results that could not be delivered are retried on the next start or with `/poll webhook <id>`.

### The role is not pinged
`mention-role-id` must be the numeric role id (Developer Mode → right-click the role → Copy ID). Only the results message pings the role, not the "poll started" message.

### "There are already 5 polls running or scheduled"
Raise `creation.max-active-polls` or end/cancel a poll.

### A config poll is not created
Look for a warning in the console starting with `polls.<key>`; it names the problem (missing title, fewer than 2 options, invalid duration, bad key name). Also check `enabled` and that the same entry has no poll already running.

### A config poll starts at the wrong time
`start` is in the **server's** time zone (the JVM's default), format `2026-12-24 18:00`.

### Clickable chat links run a literal `/<label> vote <id>`
Fixed in 1.0.1. Update the plugin. If you edited messages, keep the placeholders as in the bundled file.

### My config changes are not applied
Run `/poll reload`. `storage` and `command.aliases` need a restart. Invalid values are replaced by defaults with a warning in the console.

### Polls on my second server do not show up
Both servers must use the same MariaDB database (SQLite files are per server). Changes appear within `storage.sync-interval-seconds`.

## Still stuck?

Open an issue on [GitHub](https://github.com/filasik/justPolls/issues) with your server version, JustPolls version, the relevant part of `config.yml` (remove passwords and the webhook URL) and the console log.
