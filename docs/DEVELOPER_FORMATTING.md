# ClansCore developer-only formatting (before compilation)

Edit **`api/src/main/java/dev/clans/api/DeveloperStyle.java`**, then compile ClansCore. This is the source of formatting for ClansCore layouts and system messages. LPC owns public formatting in its own config; ClansChat is retired. It is a Java source file, not a server config resource. Core server-side YAML cannot override these layouts or message templates. The LPC adapter and Core notices use this source palette for relation-coloured clan names. Only Core `messages` and `text-replacements` YAML overrides are ignored. LPC formats, prefixes and message settings belong to LPC.

```powershell
.\mvnw.cmd clean verify
```

Stop the server, replace `ClansCore.jar`, remove the old `ClansChat.jar`, and restart. `/clanadmin reload` cannot apply edited Java source. Use `/lpc reload` to apply LPC format edits. Do not install the API JAR separately; the shared style class is included inside Core.

## Where to edit

| Source constant(s) | Controls |
|---|---|
| `SYSTEM`, `VALUE`, `PLAYER_NAME`, `SAME_CLAN`, `ENEMY_CLAN`, `NEUTRAL_CLAN` | Core sentence text, aqua data values/player names, fallback relation colours |
| `HELP_HEADER`, `HELP_ROW`, `HELP_FOOTER`, help titles and page size | Normal/staff help appearance |
| `INFO_LINES` | Info layout: reorder, insert, remove or add blank lines |
| `ADMIN_INFO_EXTRA` | Extra staff info rows, including internal ID |
| `MEMBERS_HEADER`, `MEMBER_GROUP_HEADER`, `MEMBER_ROW`, `MEMBERS_FOOTER`, rank section names/statuses/page size | Ranked member listings |
| `LIST_HEADER`, `LIST_ROW`, `LIST_FOOTER`, list page size | Clan directory |
| `INVITES_HEADER`, `INVITE_ROW`, `INVITES_FOOTER` | Invitation display |
| `PEACE_HEADER`, `PEACE_ROW` | Incoming offers in info |
| `CORE_MESSAGES` | Core success/error/line/notification wrappers, six war/peace templates and button labels |
| `ACCEPT`, `DECLINE` | Button colours |
| `RESPONSE_REPLACEMENTS` | Ordered Java regex replacements of generated response/error wording, inserted safely as plain text |

Core layouts and system-message templates support **MiniMessage**. Aqua is `&b`'s equivalent, yellow `&e`, green `&a`, red `&c`. Use `<aqua>`, `<yellow>`, `<green>`, `<red>` in layout strings, or the matching `NamedTextColor` constant for palette values. There is no automatic `[Clans]` prefix. Clan placeholders contain actual names without rank stars; their component colours come from the viewer's relationship and remain intact under surrounding template colours.

Example info layout:

```java
public static final List<String> INFO_LINES = List.of(
    "<yellow><bold>Clan profile</bold></yellow> — {clan}",
    "",
    "<aqua>Leader:</aqua> {leader}",
    "<aqua>Motto:</aqua> {motto}",
    "<aqua>Founded:</aqua> {founded}",
    "<aqua>Members:</aqua> {total} <aqua>Online:</aqua> {online}",
    "<aqua>Joining:</aqua> {policy}",
    "<aqua>Enemies:</aqua> {enemies}");
```

Member rows follow info in Leader/Elders/Members sections, with pagination, so layout changes do not silently hide members. Pages are selected before section headers are inserted. Section counts are full-clan rank totals; only members on the current page are listed below them. Empty ranks are shown on page 1. Page-size constants must be positive. Help continues to enforce permission/membership/rank filters; templates do not change access rules.

## Safe placeholders

| Layout | Available placeholders |
|---|---|
| Help header/footer | `{title}`, `{root}`, `{page}`, `{pages}` |
| Help row | `{command}`, `{usage}` |
| Info/admin extra | `{clan}`, `{id}`, `{motto}`, `{founded}`, `{policy}`, `{leader}`, `{total}`, `{online}`, `{offline}`, `{enemy-count}`, `{enemies}`, `{command}`, `{page}`, `{pages}` |
| Member header/footer | Same as info |
| Member rank section | `{rank}`, `{count}`, `{empty}` (inline aqua None only for empty ranks) |
| Member row | `{rank}`, `{username}`, `{status}` |
| Clan list header/footer | `{page}`, `{pages}` |
| Clan list row | `{clan}`, `{total}`, `{policy}` |
| Invitation / incoming peace row | `{clan}`, `{expires}` |
| Incoming peace header | `{offers}` (aqua None when empty; empty component when rows follow) |
| Core response wrappers | `{text}` |
| War/peace notices | `{otherclan}`, `{clan}` |

Placeholder values are components and are never parsed again as markup. Mottos, usernames, clan names and generated command text cannot inject colours or click events. Literal usage strings such as `<player>` stay visible. To write a literal `<page>` directly in a MiniMessage Java template, use `\\<page>` in the Java string (two backslashes in source produce one parser escape). Existing source footer templates show this pattern.

LPC public templates and LuckPerms formatting are edited by the server operator in LPC/config.yml. See LPC.md for clan placeholders. ClansChat is not built or installed.

Domain-specific wording and command syntaxes also exist in `core/.../ClanService.java` and `core/.../ClansCore.java`; generated responses use the central source wrappers. Use `RESPONSE_REPLACEMENTS` to change that wording without modifying domain logic, or edit those Java strings directly. Console audit/error logs and native server permission-denial messages are separate from player-facing plugin layouts.
