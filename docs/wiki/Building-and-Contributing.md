# Building and Contributing

## Requirements

- JDK **25** (the Gradle toolchain will use it)
- Git

## Build

```bash
git clone https://github.com/filasik/justPolls.git
cd justPolls
./gradlew build
```

The plugin jar is written to `build/libs/`. The build compiles with `-Xlint:all -Werror` (warnings are errors) and runs the unit tests.

## Run a test server

```bash
./gradlew runServer                       # downloads Paper and starts it with the plugin
./gradlew runServer -PrunDir=/path/to/srv # reuse an existing test server directory
./scripts/smoke-test.sh                   # starts a clean Paper, checks the plugin enabled without errors, stops it
```

The smoke test also runs in CI on every push and pull request.

## Project layout

| Package | Contents |
| :--- | :--- |
| `me.skerik.justpolls` | Plugin entry point (`JustPolls`), library loader (`JustPollsLoader`), scheduler abstraction (`Scheduler`, `PaperScheduler`) |
| `command` | `/justpolls` and its subcommands (Brigadier) |
| `config` | `Settings` (validated snapshot of `config.yml`), `Messages`, `ConfigUpdater` |
| `service` | `PollService`: poll lifecycle, free of Bukkit so it can be unit tested |
| `storage` | `Database` (HikariCP), `PollStore`, versioned `Migrations` |
| `gui` | Dialog menus, click handling, auto-open, chat announcements |
| `discord` | Webhook payloads (`WebhookMessages`) and delivery (`WebhookSender`) |
| `model`, `util` | Poll records, durations, voter hashing, update checker, bStats |

Runtime libraries (SQLite, MariaDB driver, HikariCP) are listed in `src/main/resources/libraries.txt` and downloaded by Paper at the first start; only bStats is shaded (relocated). Versions live in `gradle.properties`.

## Rules worth knowing

- **Threads**: no blocking I/O on the main thread; schedule through `Scheduler` (global / entity / async) so Folia keeps working.
- **New config option**: add it to `config.yml` with a comment, raise `config-version`, read it in `Settings`, extend `ConfigUpdaterTest`.
- **Schema change**: append a new entry to `Migrations.VERSIONS` (never edit old ones) and add an upgrade test.
- **New message key**: add it to `messages_en.yml` and every bundled locale (`MessagesFilesTest` checks this). A new bundled language also goes into `Messages.BUNDLED_LOCALES`.
- **Changelog**: update `CHANGELOG.md`; the section `## <version>` is used as the release notes.

## Debug hook

Starting the server with `-Djustpolls.debug=true` adds a console-only `justpolls debug-click <player> <key> <snbt>` command that simulates a dialog click for test harnesses. Every permission check still applies to the target player. It is off by default.

## Releases

Pushing a tag `vX.Y.Z` runs the release workflow: smoke test, GitHub Release with the jar and the changelog section, then upload to Modrinth and Hangar. See `docs/RELEASE_CHECKLIST.md` in the repository.

## Contributing

- Open an issue before larger changes.
- Keep pull requests focused and update `CHANGELOG.md`.
- Test on Paper (`./gradlew runServer`).
- By contributing you agree your code is licensed under the project license (MIT).
- Security issues: please report privately, see [Privacy](Privacy#reporting-a-vulnerability).
