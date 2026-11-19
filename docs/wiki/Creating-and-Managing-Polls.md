# Creating and Managing Polls

Creating polls needs `justpolls.create`; managing them needs `justpolls.admin` (both default to op). See [Commands and Permissions](Commands-and-Permissions).

## Creating a poll in the menu

Run `/poll create`. The dialog has:

| Field | Notes |
| :--- | :--- |
| **Question** | Required, up to 100 characters. |
| **Description** | Optional, up to 500 characters. Shown in the menu and the Discord message. |
| **Option 1 … N** | At least two, up to 60 characters each, must differ from each other (case-insensitive). N is `creation.max-options`. Empty fields are skipped. |
| **How long should it run?** | A drop-down of `creation.duration-presets` plus **Custom...**. |
| **Custom duration** | Used only when "Custom..." is selected, e.g. `45m`, `2d`, `1w`. |
| **Send the results to Discord** | Shown when Discord is enabled; the default is `discord.send-by-default`. |
| **Open the menu automatically for players** | Whether this poll uses [auto-open](Configuration#auto-open). |

If something is wrong (missing question, duplicate options, duration outside `min-duration`/`max-duration`, ...) the form reopens with your input kept and the problem shown at the top.

## Creating a poll from one line

```
/poll quick <duration> <question> | <option> | <option> ...
```

```
/poll quick 1h Best season? | Summer | Winter
/poll quick 3d Which minigame next? | Build battle | Parkour race | PvP arena
```

The same validation as the menu applies. The poll uses the default Discord switch and has auto-open on. `quick` also works from the console, so you can trigger polls from other plugins or schedulers.

## Limits

At most `creation.max-active-polls` polls (default 5) may run or be scheduled at the same time. Creating another one is refused with a message until one ends.

## What happens when a poll starts

- A chat announcement with a clickable **[click to vote]** button and a sound (`announce` section).
- The voting menu pops up for online players after `auto-open.on-start-delay` (if auto-open is on for the poll).
- Optionally a "poll started" Discord message (`discord.announce-start`).

## Managing polls

`/poll manage` opens the staff menu: every running and scheduled poll, a **+ New poll** button and **Finished polls**. Clicking a poll shows its status, creator, time left, number of votes, Discord and auto-open flags, and the actions that fit its status (for example, **End now** and **Open menu** only for running polls, **Send to Discord** only for finished ones):

| Button | Command | Effect |
| :--- | :--- | :--- |
| **Results** | `/poll results <id>` | Live results (staff always see them). |
| **+1h / +1d / -1h** | `/poll extend <id> <duration>` | Move the end. The command accepts any duration (`+2h`, `-30m`). Refused if the new end would be in the past. |
| **Open menu for all players** | `/poll open <id>` | Pops the voting menu up for every online player who may vote and has not voted. Ignores the auto-open limits and opt-outs. |
| **End now** | `/poll end <id>` | Ends the poll and publishes the results (chat + Discord) as if time had run out. |
| **Cancel (no results)** | `/poll cancel <id>` | Stops the poll. Votes stay in the database but no results are published. |
| **Delete with votes** | `/poll delete <id>` | Removes the poll and all its votes. Cannot be undone. |
| **Send to Discord** | `/poll webhook <id>` | Sends the results of a finished poll to Discord (again). |

End, cancel and delete ask for confirmation in the menu.

## When a poll ends

- Chat summary for everyone online: the winner with percentage and votes (or a tie, or "Nobody voted"), then every option.
- If the poll has Discord on, the results embed is sent. If Discord is unreachable the message is kept as **pending** and retried on the next server start, or immediately with `/poll webhook <id>`.
- The poll moves to the history (`/poll history`). Cancelled polls are hidden from players' history but visible to staff.

## Statuses

| Status | Meaning |
| :--- | :--- |
| scheduled | Created with a future start time; starts by itself. |
| running | Players can vote. |
| ended | Time ran out or ended early; results published. |
| cancelled | Stopped by staff; no results published. |
