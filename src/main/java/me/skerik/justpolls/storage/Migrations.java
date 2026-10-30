package me.skerik.justpolls.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Versioned schema. Rules:
 * <ul>
 *   <li>Append a new list to {@link #VERSIONS} for every schema change; NEVER edit or reorder an old one
 *       (servers in the wild have already run it).</li>
 *   <li>Each version runs in one transaction and is recorded in the {@code schema} table. SQLite rolls a failed
 *       version back completely; MariaDB DDL auto-commits, so keep a version small and, where possible, idempotent.</li>
 *   <li>Write plain SQL that works on both dialects. Placeholders: {@code {P}} table prefix, {@code {ID}} auto-increment
 *       primary key, {@code {BLOB}} binary column. For anything else (upserts!) branch on the dialect in the store class.</li>
 *   <li>Add an upgrade test for every new version: migrate to the previous version with
 *       {@link #migrate(Database, int)}, insert old-shaped data, migrate to latest, assert the data survived.</li>
 * </ul>
 */
public final class Migrations {

    /** Prefix of every table (several plugins can share one MariaDB database). */
    public static final String PREFIX = "justpolls_";

    private Migrations() {
    }

    public static String table(String name) {
        return PREFIX + name;
    }

    private static final List<List<String>> VERSIONS = List.of(
            // v1 (0.1.0): polls, options, votes (voter is a pseudonymous hash, see Voters), meta (voter salt)
            List.of(
                    """
                    CREATE TABLE {P}polls (
                      id {ID},
                      title VARCHAR(256) NOT NULL,
                      description TEXT NOT NULL,
                      status VARCHAR(12) NOT NULL,
                      start_at BIGINT NOT NULL,
                      end_at BIGINT NOT NULL,
                      created_at BIGINT NOT NULL,
                      created_by VARCHAR(64) NOT NULL,
                      discord INT NOT NULL,
                      auto_open INT NOT NULL,
                      open_delay BIGINT NOT NULL,
                      permission VARCHAR(128) NOT NULL,
                      config_key VARCHAR(64) NULL,
                      repeat_ms BIGINT NOT NULL,
                      webhook_pending INT NOT NULL
                    )""",
                    "CREATE INDEX {P}polls_status ON {P}polls (status)",
                    "CREATE INDEX {P}polls_config ON {P}polls (config_key)",
                    """
                    CREATE TABLE {P}options (
                      poll_id BIGINT NOT NULL,
                      idx INT NOT NULL,
                      label VARCHAR(256) NOT NULL,
                      PRIMARY KEY (poll_id, idx)
                    )""",
                    """
                    CREATE TABLE {P}votes (
                      poll_id BIGINT NOT NULL,
                      voter VARCHAR(64) NOT NULL,
                      option_idx INT NOT NULL,
                      voted_at BIGINT NOT NULL,
                      PRIMARY KEY (poll_id, voter)
                    )""",
                    """
                    CREATE TABLE {P}meta (
                      k VARCHAR(32) NOT NULL PRIMARY KEY,
                      v VARCHAR(128) NOT NULL
                    )"""));
            // v2 example, once you need it:
            // List.of("ALTER TABLE {P}polls ADD COLUMN something INT NOT NULL DEFAULT 0")

    public static int latestVersion() {
        return VERSIONS.size();
    }

    /** @return the schema version after migrating */
    public static int migrate(Database db) throws SQLException {
        return migrate(db, VERSIONS.size());
    }

    /** Migrates up to {@code target} (tests use this to simulate an older install). */
    public static int migrate(Database db, int target) throws SQLException {
        return db.call(c -> {
            ensureVersionTable(c);
            int current = currentVersion(c);
            for (int v = current + 1; v <= Math.min(target, VERSIONS.size()); v++) {
                int version = v;
                Database.transaction(c, tx -> {
                    for (String sql : VERSIONS.get(version - 1)) {
                        try (Statement st = tx.createStatement()) {
                            st.execute(dialect(sql, db.dialect()));
                        }
                    }
                    try (PreparedStatement ps = tx.prepareStatement("INSERT INTO " + table("schema") + " (version) VALUES (?)")) {
                        ps.setInt(1, version);
                        ps.executeUpdate();
                    }
                    return null;
                });
            }
            return currentVersion(c);
        });
    }

    private static void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + table("schema") + " (version INT NOT NULL PRIMARY KEY)");
        }
    }

    private static int currentVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(version) FROM " + table("schema"))) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String dialect(String sql, DbConfig.Dialect dialect) {
        String withPrefix = sql.replace("{P}", PREFIX);
        return switch (dialect) {
            case SQLITE -> withPrefix.replace("{ID}", "INTEGER PRIMARY KEY AUTOINCREMENT").replace("{BLOB}", "BLOB");
            case MARIADB -> withPrefix.replace("{ID}", "BIGINT PRIMARY KEY AUTO_INCREMENT").replace("{BLOB}", "LONGBLOB");
        };
    }
}
