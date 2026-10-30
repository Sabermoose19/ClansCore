# ClansCore API

`ClanNameStyle` remains an optional service for external integrations. With no provider, Core notices use the developer-defined relation palette. The LPC adapter uses that same palette directly. ClansChat is retired and supplies no service or formatter.

Maven module `dev.clans:clans-api:1.2.0` is a **provided** compile dependency for extensions. Run `mvnw install` to install reactor artifacts locally. A third-party plugin must declare `depend: [ClansCore]` in plugin.yml, compile with Java 25, and must not shade its own copy of the API. Core's optional PlaceholderAPI API dependency is declared provided in `core/pom.xml`; no third-party formatter is bundled.

```java
ClansApi api = getServer().getServicesManager().load(ClansApi.class);
ClansApi.Snapshot snapshot = api.snapshot();
ClansApi.Clan clan = snapshot.clan(playerUuid);
AutoCloseable registration = api.registerExtension(
    "example", "myplugin.example", "[message...]",
    (sender, args) -> { /* invoked on the original command context */ });
AutoCloseable subscription = api.subscribe(next -> {
    // Database worker: immutable snapshot only. Schedule any player operations.
});
// Close both handles on extension disable.
```

Snapshots, clans, members, invitations, and peace requests are immutable records. All returned collections are immutable copies. Capture one snapshot for related reads; repeated calls may return newer generations. IDs and player membership keys are UUIDs. `snapshot.clan(player)` returns null when unclanned; `named(name)` uses case-insensitive lookup. Expiry timestamps are UTC epoch milliseconds; readers must ignore expired records. Rename does not change IDs.

`registerExtension` reserves a unique lowercase subcommand below Core's sole `/clan` root. Core checks the supplied permission before invoking the handler, integrates the usage into help, and explicitly adds the permission as a child of `clanscore.player.*`. Declare that node in the extension's plugin.yml with an appropriate default. Close removes the subcommand/help/wildcard relationship. Extensions must enforce their own membership/rank requirements; permission grants alone do not confer clan rank. Aliases owned by external extensions must attach their own explicit wildcard children if they use separate permission nodes. The LPC integration registers no Core chat command or private-chat alias.

`subscribe` callbacks run synchronously **after commit and cache publication** on the single database worker. They must be fast and non-blocking. Never call unsafe Bukkit operations there. Listener failures cannot roll back an already committed operation. No mutable domain/database service is published through this API; ClansCore owns all mutations and authorization.

## Threading and persistence

Command handlers capture immutable arguments and submit domain mutations to one dedicated executor. On this worker, current membership, rank, invite validity, limits, and confirmation target are revalidated. Each mutation builds a replacement state, validates invariants, executes one SQLite transaction, and publishes only after commit. On failure the old snapshot remains visible and the sender receives a private failure. SQLite foreign keys, a case-insensitive unique name constraint, UUID membership primary key, partial unique Leader index, and a deferred `(Leader UUID, clan ID, LEADER)` foreign key ensure exactly one correct Leader at commit.

The first release uses a complete normalized-state transaction rather than incremental row updates. This is deliberately straightforward and atomic but has O(total stored clan data) write cost per mutation. Chat reads are memory-only. Benchmark large installations; incremental persistence can be added without changing the public snapshots.

Player sends use `Player.getScheduler()`; console sends use the global-region scheduler. Public rendering uses immutable domain snapshots and LuckPerms cached data; it never queries Bukkit world/entity state or SQL. Plugin join/quit events maintain concurrent online-player references; stable UUID/name getters identify the chat source/viewer. No blocks, world state, scoreboard teams, tab names, or overhead tags are touched. Region scheduling is therefore unnecessary. The database executor is owned by ClansCore and drains before the connection closes. No BukkitScheduler/global reload mechanism is used.

Schema migration 0→1 is transactional and sets `PRAGMA user_version=1`. Opening an unknown newer schema refuses startup rather than modifying it. Future releases must add ordered migration steps and tests; back up before upgrades. Confirmation state deliberately does not persist. There is no private-channel state.
