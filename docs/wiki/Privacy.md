# Privacy

JustPolls is built so that **results never show who voted for what**.

## What players and staff can see

- Results are always **totals and percentages**: in chat, in the menus and on Discord.
- There is **no command or menu** that lists voters or shows how a particular player voted. Each player can only see their own vote.
- Discord messages never contain player names.

## What is stored

A vote is stored as:

| Column | Value |
| :--- | :--- |
| `poll_id` | The poll |
| `voter` | `SHA-256(salt : poll id : player UUID)` |
| `option_idx` | The chosen option |
| `voted_at` | Time of the vote |

The player's UUID or name is **not** stored next to the vote. The hash is enough to answer "has this player already voted in this poll?" and nothing more. Because the poll id is part of the hash, the same player gets a different code in every poll, so a database dump does not even show which votes in different polls came from the same person.

The poll creator's name (`created_by`) is stored and shown to staff in the manage menu.

## Limits of this protection

The salt is stored in the same database (`justpolls_meta`). Someone with **full database access and a list of player UUIDs** could compute the hashes and match votes to players. This is privacy by design against casual viewing (dumps, screenshots, staff curiosity), not a cryptographic guarantee against a database administrator. If that matters for your server, restrict who can access the database.

## Other data

- **Auto-open opt-out** (`/poll autoopen off`) is stored in the player's own data (persistent data container), not in the database.
- **Update check**: a request to the GitHub API for the latest release (`update-check: false` turns it off).
- **bStats**: anonymous server statistics, including the configured language and storage type (`metrics: false` turns it off, as does the global switch in `plugins/bStats/config.yml`).
- **Library download**: on the first start the database drivers are fetched from Maven Central.

## Reporting a vulnerability

Please do not open a public issue. Use **Report a vulnerability** on the [Security tab](https://github.com/filasik/justPolls/security) of the repository. Only the latest release receives security fixes.
