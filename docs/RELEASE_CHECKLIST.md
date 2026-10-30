# Release checklist (before tag vX.Y.Z)

- [ ] `./gradlew build` is green (compiles with `-Xlint:all -Werror`, unit tests pass)
- [ ] `./scripts/smoke-test.sh` passes (clean Paper, plugin enabled, no errors in the log)
- [ ] Tested by hand on Paper (`docs/MANUAL_TEST.md`): main features, commands, permissions (as OP and without OP)
- [ ] Works without optional dependencies (soft-depend)
- [ ] No blocking I/O on the main thread; everything scheduled through `Scheduler`
- [ ] New config option => added to `config.yml` with a comment AND `config-version` raised; `ConfigUpdaterTest` extended
- [ ] New schema change => NEW entry appended to `Migrations.VERSIONS` (old ones untouched) AND an upgrade test
- [ ] New message key => present in `messages_en.yml` and every bundled locale (`MessagesFilesTest`)
- [ ] First start on a machine with internet works (libraries are downloaded by the loader); the jar contains only bStats as a shaded library
- [ ] `gradle.properties`: `pluginVersion`, `paperApiVersion`, `apiVersion`, `minecraftVersion` current; `bstatsId` set to the real bStats plugin id
- [ ] `CHANGELOG.md`, `docs/modrinth.md`, `docs/spigot.bbcode.txt` match the real code
- [ ] Icon, banner (`scripts/render-assets.sh`), at least 2 gallery images
- [ ] README: commands, permissions, configuration, license
- [ ] Telemetry only bStats with an off switch; no obfuscated or downloaded code (the loader only fetches the listed libraries from Maven Central)
- [ ] New Modrinth project created by hand, `MODRINTH_TOKEN` set in GitHub secrets
- [ ] SpigotMC: manual upload (description from `docs/spigot.bbcode.txt`)
- [ ] Tag `vX.Y.Z` -> CI publishes
