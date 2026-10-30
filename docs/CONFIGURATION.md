# Runtime configuration

ClansCore formatting stays developer-only in DeveloperStyle.java. LPC owns public chat formats and LuckPerms integration; remove the retired ClansChat.jar. See [LPC integration](LPC.md) for tokens, setup and the untested status.

Core runtime settings live in plugins/ClansCore/config.yml:

| Setting | Default | Behaviour |
|---|---|---|
| names.min / names.max | 3 / 16 | ASCII letters/digits/underscore; limits between 1 and 64. |
| names.reserved | bundled list | Case-insensitive reserved names. |
| motto-max | 100 | UTF-16 length; formatting/control characters forbidden. |
| member-limit | 0 | Unlimited at 0; same cap on open joining and accepting invites. |
| invite-seconds | 600 | New invitation lifetime. |
| peace-seconds | 86400 | New peace-request lifetime. |
| rename-cooldown-seconds | 86400 | Player rename cooldown; staff bypass. |
| date.timezone / date.format | UTC / yyyy-MM-dd HH:mm z | Date values; layout remains source-owned. |
| notifications | true | Private affected-player/clan notices; never server-wide broadcasts. |
| peace-buttons | true | Receiving Leader's permission-filtered response buttons. |
| integrations.placeholderapi | true | Register the internal clanscore expansion if PAPI is present; restart required. |
| integrations.lpc | true | Wrap LPC's renderer for clan tokens if LPC is present; restart required. |

Use /clanadmin reload for ordinary supported settings. Existing invitation/peace expiry timestamps do not change; reducing caps does not kick members. Changing integration switches requires a full restart; the reload command does not toggle hooks. Database schema/path is not configurable. Core messages/text-replacements YAML entries from old versions remain ignored.

LPC example:

```yaml
chat-format: "&f<%clanname%{prefix}{username-color}{name}&f> {message}"
```

%clanname% combines rank stars, actual clan name and its own trailing space; unclanned senders contribute no text/space. It is an LPC-specific alias, because native PlaceholderAPI syntax requires an underscore. The canonical PAPI fields are %clanscore_name%, %clanscore_stars%, %clanscore_chatprefix%. In LPC their values are viewer-relative; elsewhere ordinary PAPI returns plain strings. No private chat, clan tab-list or overhead nameplate features exist.

No automatic LPC config rewrite is performed. The operator controls LPC and must obtain trusted dependencies independently. LPC was not installed or run here, per the user's instruction.
