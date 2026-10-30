# Upgrade and SQLite operations

## Upgrade to 1.2.0

1. Stop the server normally and wait for the database writer to drain and the process to exit.
2. Back up plugins/ClansCore/ and the previous JAR. Keep a copy of old Chat config if you want its format as a reference.
3. Install the new ClansCore.jar and remove ClansChat.jar. The old Chat module is no longer built or distributed.
4. If desired, independently install trusted LPC/LuckPerms/PlaceholderAPI builds and configure LPC using docs/LPC.md. Core works without them.
5. Start and inspect logs. No schema migration beyond the existing version-1 schema is required. Core source layouts remain compiled; normal runtime settings retain their dedicated reload command. Integration toggles require restart.

LPC integration is implemented but explicitly untested at the user's request. Existing historical server logs do not establish its compatibility, particularly on Folia. Do not use Bukkit global reload or plugin hot-loaders.

## Backup

Stop the server and wait for exit, then copy the complete ClansCore data folder to a timestamped backup. Include clans.db and any remaining clans.db-wal / clans.db-shm files, as well as config. Do not copy only clans.db while the server is running: committed data can be in its WAL. Advanced operators can use SQLite's trusted online backup API/CLI .backup to create a consistent image.

Check a separate offline backup with PRAGMA integrity_check; (expect ok) and PRAGMA foreign_key_check; (expect no rows). Test restores away from production. Do not delete WAL/SHM files from a live server.

## Restore

Stop the server, preserve the current data folder as a rollback copy, and restore a matched backup set. Never mix old database files with WAL/SHM files from a different generation. Restore the matching plugin version when downgrading; newer unknown schema versions are refused. Start and inspect clans, UUIDs, ranks, founding dates, invites and relationships. Authentication-mode changes alter player UUIDs and are not a membership migration.

Each installation owns its own SQLite database. Do not share it between running servers, edit live rows or merge installations by copying individual tables.
