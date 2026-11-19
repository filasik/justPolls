# Player Guide

This page explains JustPolls from a player's point of view. Feel free to link it to your players.

## Voting

1. When a poll starts you see a chat announcement with a **[click to vote]** button, and usually the voting menu opens by itself a few seconds later.
2. Click the answer you want. That's it: your vote is recorded and you get a thank-you message.
3. Depending on the server's settings you then see the results so far, or a note that results are shown when the poll ends.

You can open the menu at any time with **`/poll`**. If more than one poll is running you get a list to choose from; polls you already voted in are marked with ✔.

### One vote per poll

Each player has one vote per poll. On most servers the vote is final. If the server allows changing votes, the menu shows **"You can still change your vote"** and your current answer is marked with ✔; clicking another answer moves your vote.

## The menu opens by itself

To make sure nobody misses a poll, the voting menu can pop up:

- a few seconds after a new poll starts,
- shortly after you join, if a poll is still waiting for your vote,
- as a reminder now and then, if the server has reminders switched on.

It stops as soon as you vote, and it never opens more than a few times per poll. If you find it annoying:

```
/poll autoopen off   # never open the menu by itself
/poll autoopen on    # turn it back on
```

## Seeing polls and results

| Command | What it shows |
| :--- | :--- |
| `/poll` | The running poll (or a list) |
| `/poll list` | Running and upcoming polls with **[vote]** and **[results]** links |
| `/poll history` | The latest finished polls |
| `/poll results <id>` | The results of one poll |

When a poll ends, everyone online sees the winner (or a tie) and the percentage of every option in chat.

## Is my vote anonymous?

Yes, in the sense that matters: the results only ever show totals and percentages, there is no command or menu that shows who voted for what, and the database stores a scrambled code instead of your name or UUID. Details on the [Privacy](Privacy) page.

## Rewards

Some servers give a small reward for taking part. It is given once, when you first vote in a poll (not when you change your vote).
