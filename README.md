# ClansCore 1.2.0 — LPC / PlaceholderAPI integration

ClansCore owns clans, ranks, relationships, commands and SQLite storage. LPC owns the public-chat format and LuckPerms metadata. The former ClansChat formatter is retired; remove ClansChat.jar when installing this release. Private clan chat remains removed.

## Installation

1. Stop the server and back up the ClansCore data folder.
2. Replace ClansCore.jar with `dist/ClansCore.jar`. Remove the old ClansChat.jar. Do not install the API JAR.
3. If using LPC, obtain trusted copies of LPC and LuckPerms yourself. PlaceholderAPI is optional for LPC tokens and required to expose the expansion to other plugins. No third-party runtime plugin is included in this distribution.
4. Configure LPC using the example below and start the server. Use `/lpc reload` for later LPC format changes. Preserve the existing Core database/config.

```yaml
chat-format: "&f<%clanname%{prefix}{username-color}{name}&f> {message}"
```

`%clanname%` includes rank stars, actual clan name and one trailing space; it is empty for an unclanned sender. The adapter colours stars/name green for clanmates, red for enemies and white for neutral/unclanned/non-player viewers. LPC continues to control the rest of the format. The token also works in LPC group-specific formats. See [LPC integration details](docs/LPC.md).

## Build

Set JAVA_HOME to a Java 25 JDK, then run `mvnw.cmd clean verify` on Windows or `./mvnw clean verify` on Unix. Maven 3.9.11/wrapper 3.3.4 and all dependencies are pinned. Active modules are `api` and `core`; ClansCore shades its API and SQLite driver. PlaceholderAPI is a provided API dependency and is not bundled. Build output is `core/target/ClansCore.jar`; run `scripts/package.ps1` to refresh dist and the source archive.

The old Chat module and JAR are preserved locally under `retired/` as developer backups; they are not built or packaged in the current distribution.

## Validation and compatibility

Current build: **33 existing Core regression tests passed**. LPC integration test sources compile but are excluded by default at the user's explicit request. **LPC was not built, installed, launched or tested.** No LPC compatibility claim is made based on compilation. Historical Paper/Purpur/Folia 26.1.2 smoke results belong to earlier releases and do not validate this integration.

Minecraft 26.1.2 / Java 25 remains the API target. Core is intended to run independently on Paper, Purpur and Folia. The checked upstream LPC descriptor does not declare Folia support; do not assume that LPC build can run on Folia. Verify any chosen third-party build independently before deployment. No promise is made for untested future releases or 1.21.x.

## Documentation

- [LPC / PlaceholderAPI setup, placeholders and limits](docs/LPC.md)
- [Commands and permissions](docs/COMMANDS.md)
- [Core developer formatting](docs/DEVELOPER_FORMATTING.md)
- [Runtime configuration](docs/CONFIGURATION.md)
- [Core API and threading](docs/API.md)
- [Upgrade and SQLite backup/restore](docs/OPERATIONS.md)
- [Test results and manual checklist](docs/TESTING.md)

All normal command permissions default true; administrative permissions default op. `clanscore.*` includes normal and administrative commands, with independent clan-rank checks on normal commands. Owner example: `/lp user <username> permission set clanscore.* true`. LPC permissions are separate and belong to LPC. Core never requires LuckPerms, LPC or PlaceholderAPI to manage clans.
