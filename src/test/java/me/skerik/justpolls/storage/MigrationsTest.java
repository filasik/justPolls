package me.skerik.justpolls.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationsTest {

    @TempDir Path dir;

    @Test
    void freshDatabaseReachesTheLatestVersionAndMigratingAgainIsANoop() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("fresh.db"), 2))) {
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
        }
    }

    /**
     * Pattern for every new schema version: migrate(db, previous), insert data shaped like the old version,
     * migrate(db) to the latest, assert the data survived. With only v1 there is nothing to upgrade from yet,
     * so this just proves a partial target works.
     */
    @Test
    void canStopAtAnOlderVersion() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("old.db"), 2))) {
            assertEquals(1, Migrations.migrate(db, 1));
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
        }
    }
}
