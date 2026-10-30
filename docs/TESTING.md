# Validation status — 2026-10-30

## Current release 1.2.0

The build runs only existing Core regression tests by default. **33 tests passed:** ClanServiceTest (17), DeveloperStyleTest (8), MessageFormatTest (4), PermissionMetadataTest (4).

These cover serialized concurrent creation/joining, rank authorization, atomic transfers, confirmations, invite/peace expiry, open/closed limits, mutual relationships, disband cleanup, restart persistence, failed-write cache safety and SQLite locking/constraints. Layout and permission tests cover safe literal values, page completeness, rank sections, system/peace styling, explicit wildcard children, root visibility and /c + who aliases.

No LPC binary was built, installed or executed. The integration's source tests compile but `LpcIntegrationTest` is excluded in core/pom.xml. Do not describe the integration as verified. The compiled tests include ordinary/PAPI fields, per-viewer renderer decoration, message-token shielding, scope cleanup/concurrency, cancellation/audiences and membership changes, but none were executed.

No server was started for release 1.2.0. Logs under test-results/historical belong to earlier releases, which smoke-tested the former Core/Chat combination on Paper 26.1.2 build 74, Purpur 26.1.2 build 2592 and Folia 26.1.2 build 8 with Java 25.0.5+8. Those logs are not LPC evidence. There have been no connected-client tests.

## Manual checklist for the operator (not performed here)

- Record exact server, Java, LPC, LuckPerms and PlaceholderAPI versions plus Core JAR checksum.
- Remove ClansChat.jar, install only trusted dependencies, and fully restart.
- Confirm Core manages clans with optional dependencies absent; database/ranks/relationships survive upgrade.
- Confirm Core's startup reports the PAPI expansion and LPC adapter only when those plugins are enabled.
- Put %clanname% in LPC chat-format and group overrides; verify **/*/no stars and actual clan names.
- With multiple accounts, confirm same clan green, enemies red, neutral/unclanned white; unclanned sender has no tag or extra separating space.
- Confirm LPC prefixes/suffixes, username/message colours, permission checks and its reload remain in control.
- Type literal %clanname% and %clanscore_...% in a message: content must not be expanded.
- Test canonical PAPI tokens with offline and online UUID membership; rename/leave/disband reflects committed state; unknown parameters remain unresolved.
- Run /papi reload and confirm the internal expansion remains registered.
- Test cancelled/local/bridge chat behaviour: no audience change, duplication or private routing by Core.
- Verify /c, /clan who, permissions, Core source formatting and SQLite backups using the normal command/operation references.
- For Folia, independently establish third-party compatibility first. The inspected LPC descriptor does not opt in; do not force-load it based on Core's declaration.

## Limits

The single database writer still persists the full normalized state per mutation; benchmark large datasets. Unknown player targets must be seen locally/cached. Mottos forbid markup/control characters; string limits count UTF-16 units. Expired records are logically ignored immediately and physically cleaned on the next successful mutation. No integration or future-server compatibility is promised without testing.
