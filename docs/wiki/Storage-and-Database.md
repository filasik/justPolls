# Storage and Database

JustPolls stores polls, options and votes in **SQLite** (default) or **MariaDB / MySQL**. All database work runs asynchronously, never on the main thread.

## SQLite (default)

Zero setup. The database is a single file, `plugins/JustPolls/justpolls.db`.

```yaml
storage:
  type: sqlite
  sqlite:
    file: justpolls.db
    pool-size: 3
```

Back it up by copying the file while the server is stopped.

## MariaDB / MySQL

Use it when you want to share polls between several servers or keep data in your existing database server.

1. Create a database and a user:
   ```sql
   CREATE DATABASE justpolls CHARACTER SET utf8mb4;
   CREATE USER 'justpolls'@'%' IDENTIFIED BY 'a-strong-password';
   GRANT ALL PRIVILEGES ON justpolls.* TO 'justpolls'@'%';
   ```
2. Configure:
   ```yaml
   storage:
     type: mariadb        # "mysql" works as well
     mariadb:
       host: localhost
       port: 3306
       database: justpolls
       user: justpolls
       password: a-strong-password
       pool-size: 6
       ssl-mode: ""
   ```
3. **Restart** the server (storage settings are not reloaded by `/poll reload`).

All tables are prefixed with `justpolls_`, so you can put them into a database shared with other plugins.

### `ssl-mode`

| Value | Meaning |
| :--- | :--- |
| `""` | Driver default. |
| `trust` | Encrypted, certificate not verified. Use this for **MySQL 8+ with its default self-signed certificate**. |
| `disable` | No TLS. Only on a trusted local network. |
| `verify-ca`, `verify-full` | Encrypted and verified; needs a proper certificate. |

If you see `Cannot use authentication plugin caching_sha2_password with a Self signed certificates`, set `ssl-mode: trust`.

## Several servers, one database

Point every server at the same MariaDB database. Then:

- A poll created on one server appears on the others within `storage.sync-interval-seconds` (default 30 s), together with the votes cast there.
- Every player still has one vote per poll across all servers.
- Starting and ending a poll is decided by the database, so exactly one server ends a poll, announces it to Discord and runs the transition; the others just pick up the new state.
- Lower `sync-interval-seconds` (minimum 5) if you want changes to show up faster.

Announcements are local: the start announcement and the automatic menu happen on the server where the poll was created (or, for a scheduled poll, the server that started it), and the end summary on the server that ended it. Players on the other servers see the poll in `/poll`, `/poll list` and on join (auto-open on join still works), and can vote normally. If every server should get the announcements, rely on Discord or create the poll where most players are.

## Tables

| Table | Contents |
| :--- | :--- |
| `justpolls_polls` | Poll id, title, description, status, start/end times, creator, Discord/auto-open flags, per-poll permission, config key, repeat interval, pending Discord flag |
| `justpolls_options` | The options of each poll |
| `justpolls_votes` | Poll id, **pseudonymous voter hash**, chosen option, time |
| `justpolls_meta` | Internal values (the voter salt) |
| `justpolls_schema` | Applied schema versions |

The schema is migrated automatically on startup. See [Privacy](Privacy) for what the voter hash is.

## If the database cannot be opened

JustPolls logs one clear line (`Cannot open the database: ... Check storage settings in config.yml.`) and disables itself; the server keeps running. Fix the settings and restart.
