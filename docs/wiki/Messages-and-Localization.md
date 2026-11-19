# Messages and Localization

## Choosing a language

```yaml
locale: en   # or cs
```

Bundled languages: **English** (`en`) and **Czech** (`cs`). Run `/poll reload` after changing it.

## Editing texts

On the first start, `messages_en.yml` and `messages_cs.yml` are copied to `plugins/JustPolls/`. Edit the file of your locale and run `/poll reload`.

Each key is looked up in this order:

1. your `plugins/JustPolls/messages_<locale>.yml`,
2. the bundled file of the same locale,
3. the bundled English file.

So you only need to keep the keys you actually changed, and a newer version never shows a missing text.

## Format

In-game texts use [MiniMessage](https://docs.advntr.dev/minimessage/format.html):

```yaml
prefix: '<dark_gray>[<gold>JustPolls<dark_gray>]<reset>'
vote:
  success: '<prefix> <green>Thanks! Your vote for <white><option></white> was recorded.'
```

- `<prefix>` inserts the `prefix` key.
- Placeholders in `<angle brackets>` (`<id>`, `<title>`, `<option>`, `<time>`, `<label>`, ...) are filled in by the plugin. Keep them.
- `<label>` is the command name used in help texts and clickable links (the first alias, e.g. `poll`).
- Click and hover tags work, for example `<click:run_command:'/<label> vote <id>'>`.
- A list value (e.g. `command.info`, `announce.started`) prints one chat line per entry. `''` is an empty line.

## Sections

| Section | Used for |
| :--- | :--- |
| `error` | Error messages (not ready, no permission, unknown poll, bad duration, ...) |
| `command` | Help (`info`, `info-admin`), reload, `quick` usage |
| `poll` | `list`, `history`, `results` output and status names |
| `announce` | Chat announcements when a poll starts and ends |
| `vote` | Feedback after clicking an answer |
| `create` | Creation messages and form validation errors |
| `manage` | Results of staff actions and Discord commands |
| `autoopen` | `/poll autoopen on/off` replies |
| `gui` | Every text in the dialog menus (vote, list, results, history, create, manage, admin, confirm) |
| `update` | "A new version is available" |
| `webhook` | Discord texts (see below) |

## Discord texts

The `webhook` section is **plain text for Discord**, not MiniMessage: you can use Discord Markdown, and placeholders are in `{curly braces}`:

```yaml
webhook:
  total: 'Total votes'
  tie: 'Tie: {options}'
  votes-one: '{n} vote'
  votes-few: '{n} votes'
  votes-many: '{n} votes'
```

`votes-one`, `votes-few` and `votes-many` are plural forms (languages like Czech use all three). Percentages are formatted according to `locale`.

## Adding a language

1. Copy `messages_en.yml` to `messages_<code>.yml` (e.g. `messages_de.yml`) in `plugins/JustPolls/`.
2. Translate the values.
3. Set `locale: <code>` and run `/poll reload`.

Missing keys fall back to English. If you would like your translation bundled with the plugin, open a pull request that adds `src/main/resources/messages_<code>.yml` (see [Building and Contributing](Building-and-Contributing)).
