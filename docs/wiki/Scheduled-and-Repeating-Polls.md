# Scheduled and Repeating Polls

Besides polls created in-game, you can define polls in the `polls` section of `config.yml`. They are useful for recurring votes ("which event this weekend?") or for a poll that should start at an exact date.

## Example

```yaml
polls:
  weekly-event:
    enabled: true
    title: "Which event should we run this weekend?"
    description: "Pick one, the most voted event wins."     # optional
    options: ["Build battle", "Parkour race", "PvP arena"]   # 2-10 entries
    duration: 3d
    start: "2026-12-24 18:00"    # optional, server time; without it the poll starts at once
    repeat: 7d                   # optional, a new identical poll starts this long after the previous one started
    discord: true                # optional, default is discord.send-by-default
    auto-open: true              # optional, open the menu automatically
    open-delay: 30s              # optional, overrides auto-open.on-start-delay for this poll
    permission: "group.vip"      # optional, only players with it can see and vote
```

## Keys

| Key | Required | Description |
| :--- | :--- | :--- |
| *(entry name)* | yes | Here `weekly-event`. Letters, digits, `-` and `_`, max 64. It identifies the poll; renaming it creates a new poll. |
| `enabled` | no | `false` stops the entry from creating polls (default `true`). |
| `title` | yes | Up to 100 characters. |
| `description` | no | Up to 500 characters. |
| `options` | yes | 2 to 10 entries, each up to 60 characters. |
| `duration` | yes | How long each poll runs, e.g. `1h`, `3d`. |
| `start` | no | First start, `YYYY-MM-DD HH:MM` in the **server's time zone**. Without it the poll starts at once. |
| `repeat` | no | Interval between starts, e.g. `7d`. Without it the poll runs once. |
| `discord` | no | Send the results to Discord. Default `discord.send-by-default`. |
| `auto-open` | no | Open the voting menu automatically. Default `true`. |
| `open-delay` | no | Overrides `auto-open.on-start-delay` for this poll. |
| `permission` | no | Only players with this permission (and `justpolls.vote`) see and vote in the poll. |

An invalid entry is skipped with a warning in the console that names the key and the problem.

## How it behaves

- Config polls are checked at startup, after `/poll reload`, and every `storage.sync-interval-seconds`.
- An entry creates its poll **once**. If a poll from that entry is already scheduled or running, nothing happens, so editing the config never resets votes of a running poll. Changes to title, options etc. apply to the **next** poll the entry creates.
- With `repeat`, the next poll starts `repeat` after the previous one **started** (not ended), once the previous one is over. With `duration: 3d` and `repeat: 7d` you get a 3-day poll every week at the same time.
- Removing an entry or setting `enabled: false` stops future polls; a poll that is already running continues and can be managed in the menu like any other.
- Config polls are not blocked by `creation.max-active-polls`.
- If the server was offline when a repeat was due, the poll is created when the server starts again.

## Scheduling a one-off poll at a specific time

Use a `start` without `repeat`:

```yaml
polls:
  new-year-map:
    title: "Which map should we open on New Year's Eve?"
    options: ["Desert", "Islands", "Mountains"]
    duration: 2d
    start: "2026-12-29 20:00"
```

It shows as **scheduled** in `/poll manage` and `/poll list` until it starts.
