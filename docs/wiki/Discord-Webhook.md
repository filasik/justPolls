# Discord Webhook

JustPolls can post poll results to a Discord channel through a webhook. No bot is needed.

## Setup

1. In Discord: **Channel settings → Integrations → Webhooks → New Webhook → Copy Webhook URL**.
2. In `plugins/JustPolls/config.yml`:
   ```yaml
   discord:
     enabled: true
     webhook-url: "https://discord.com/api/webhooks/123456789012345678/abcdef..."
   ```
3. Run `/poll reload`, then `/poll webhook-test`. A "✅ JustPolls – The webhook works" message should appear in the channel.
4. Create a poll. Each poll has a **Send the results to Discord** switch in the creation menu (default `discord.send-by-default`).

Only real Discord webhook URLs (`https://discord.com/api/webhooks/<id>/<token>`, also `discordapp.com`, `canary.`, `ptb.`) are accepted. Anything else switches Discord off with a warning in the console, so a typo can never make your server call another address. The URL contains a secret token: JustPolls never writes it to the log, and you should not share it.

## Options

| Option | Default | Description |
| :--- | :--- | :--- |
| `enabled` | `false` | Master switch. |
| `webhook-url` | `""` | The webhook URL. |
| `username` | `JustPolls` | Name shown as the message author. |
| `avatar-url` | `""` | Picture of the author (a public image URL). |
| `mention-role-id` | `""` | Numeric id of a role to ping with the **results** message. In Discord enable Developer Mode, right-click the role, **Copy ID**. |
| `color` | `#5865F2` | Embed colour. |
| `announce-start` | `false` | Also post a message when a poll starts. |
| `bar-width` | `20` | Length of the result bars (5–30). |
| `send-by-default` | `true` | Default of the per-poll switch. |
| `server-name` | `""` | Added to the footer, e.g. your server's name. |

## The results message

When a poll with Discord on ends, an embed is posted with:

- the question as the title (📊) and the description,
- one line per option: 🏆 for the winner(s), a text bar, the percentage and the number of votes,
- **Total votes**, **Winner** (or the tied options, or "Nobody voted") and **Duration**,
- a footer with `JustPolls · Poll #<id>` and your `server-name`, and the end time.

The message never contains player names. Mentions are locked down: only the configured role can be pinged, so a poll titled `@everyone` cannot ping anybody.

A rendered example is in the repository: [`docs/gallery/discord-results.png`](https://github.com/filasik/justPolls/blob/main/docs/gallery/discord-results.png).

## The "poll started" message

With `announce-start: true`, a 🗳️ **New poll: …** embed lists the options and the end time (as a Discord timestamp that shows the remaining time in each reader's time zone).

## Delivery and retries

- Messages are sent asynchronously, so the server never waits on Discord.
- Discord's rate limit (HTTP 429) is honoured, and server errors or network problems are retried up to 4 times.
- If delivery still fails, the poll keeps a **pending** flag. Pending results are sent automatically on the next server start, or immediately with `/poll webhook <id>`.
- `/poll webhook <id>` also re-sends the results of any finished poll, for example after you changed the channel.
- When several servers share one database, only the server that actually ends the poll sends the message, so it is never posted twice.

## Translating the Discord texts

The labels ("Total votes", "Winner", ...) are in the `webhook` section of `messages_<locale>.yml`. They are plain text with Discord Markdown and `{curly}` placeholders, not MiniMessage. See [Messages and Localization](Messages-and-Localization).
