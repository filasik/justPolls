## 1.0.0
- First version: timed polls (seconds to weeks), created in a dialog menu, with `/justpolls quick` or from `config.yml`
- Voting menu built on Minecraft's dialog screens; one vote per player and poll (optional vote change)
- Automatic opening of the menu after a poll starts, on join and as reminders; per-player opt-out
- SQLite (default) and MariaDB/MySQL storage, asynchronous; pseudonymous vote storage
- Results in chat and as a formatted Discord webhook embed (percentages, totals, winner), with retry and rate-limit handling
- Staff menu: extend, shorten, end, cancel, delete, open for everybody, resend to Discord
- Scheduled and repeating polls in `config.yml`
- English and Czech translation
