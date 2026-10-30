# LPC and PlaceholderAPI

## Recommended LPC format

```yaml
chat-format: "&f<%clanname%{prefix}{username-color}{name}&f> {message}"
```

LPC owns the full layout, LuckPerms prefixes/suffixes, name/message colouring and message colour permissions. Core only supplies clan data and wraps LPC's public Paper renderer to fill clan tokens per viewer. It does not cancel messages, send private chat, change audiences, set tab names or use Minecraft internals.

Examples with an otherwise empty LuckPerms prefix:

```text
<**Alpha Alice> Hello
<*Alpha Elder> Hello
<Alpha Member> Hello
<Alice> Hello
```

The clan name and stars share relationship colours from Core's developer palette: same green, enemy red, neutral/unclanned white. The space following the combined clan token is white. An unclanned sender contributes an empty string, including no space. Do not add another space after the token. User message content containing `%clanname%` or a PAPI placeholder stays literal.

## Placeholder reference

| Token | Result | Where supported |
|---|---|---|
| `%clanname%` | Combined stars + name + trailing space | LPC adapter only |
| `%clanscore_chatprefix%` | Same combined result | LPC adapter; ordinary PlaceholderAPI expansion elsewhere |
| `%clanscore_name%` | Actual name only | LPC adapter / PlaceholderAPI |
| `%clanscore_stars%` | Leader **, Elder *, Member empty | LPC adapter / PlaceholderAPI |

Standard PlaceholderAPI requires `%identifier_parameters%` syntax. Its parser leaves `%clanname%` unchanged because it has no underscore; Core's LPC adapter supplies that exact short alias. It is **not** a globally registered PAPI placeholder: `/papi parse ... %clanname%` is not expected to resolve it. Use the `%clanscore_...%` forms in other PAPI consumers. Those return plain strings without viewer-relative colouring outside the LPC event; ordinary PAPI has no viewer context.

Core registers an internal persistent `clanscore` expansion when PlaceholderAPI is enabled. It survives `/papi reload`, unregisters on Core shutdown, and reads immutable snapshots only, including offline UUID membership. No eCloud download is required for this custom expansion. Unknown parameters remain unresolved; an unclanned/null player returns an empty value.

## Event ordering and safety

Core soft-depends on LPC and PlaceholderAPI so LPC's HIGHEST chat handler registers first. A LOWEST Core handler starts an event-scoped token context, allowing PAPI evaluation during LPC's formatter to defer the clan section until the recipient is available. Core's HIGHEST handler runs after LPC, clears the scope (including cancelled events), and decorates the existing renderer. Nested event scopes are stacked and thread-local; no mutable global sender/viewer is shared across chats.

The decorator asks LPC to render with a unique message sentinel, replaces only clan tokens in the resulting format, then restores the real message component. This prevents a player from causing format substitution by typing tokens into their message. Clan data is captured from the committed memory snapshot; no per-message SQL or network work occurs. LPC controls its own escaping, nickname and message-colour behaviour.

The adapter targets the checked standard/free LPC Paper renderer, which computes the format during its HIGHEST listener and inserts the message through the ChatRenderer argument. Another formatter, bridge or LPC fork may change event ordering, replace the renderer afterward, or ignore that argument. Such combinations are untested. This is not a generic promise of compatibility with every LPC variant. Existing audience restrictions and cancellation are preserved by Core.

## Core settings

```yaml
integrations:
  placeholderapi: true
  lpc: true
```

These optional hooks load only at startup. Install/remove dependencies or change these switches with a full restart. Core works independently if they are absent. `/clanadmin reload` does not enable/disable integrations. Remove the retired ClansChat.jar so it cannot compete with LPC. No private chat or clan-chat commands are reintroduced.

## Verification status and sources

**No LPC binary was built, installed, run or tested**, at the user's direction. Existing Core regression tests ran; integration test sources compiled but were excluded. Treat the bridge as implemented but runtime-unverified. We do not assume compilation proves compatibility.

Official sources inspected:

- [LPC Paper renderer](https://github.com/ThePM2/LPC/blob/f4dc8cf03f14f5ac4d5aa49d74f31175638f33c0/src/main/java/com/infiniteplugins/lpc/PaperChatListener.java)
- [LPC format/PAPI processing](https://github.com/ThePM2/LPC/blob/f4dc8cf03f14f5ac4d5aa49d74f31175638f33c0/src/main/java/com/infiniteplugins/lpc/LPC.java)
- [LPC plugin descriptor](https://github.com/ThePM2/LPC/blob/f4dc8cf03f14f5ac4d5aa49d74f31175638f33c0/src/main/resources/plugin.yml)
- [PlaceholderAPI expansion API](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/)
- [PlaceholderAPI parser](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/2.12.3/src/main/java/me/clip/placeholderapi/replacer/CharsReplacer.java)

The inspected LPC descriptor does not opt into Folia. Core's Folia declaration does not make LPC Folia-compatible. Use an independently verified compatible LPC build or run this combination on Paper/Purpur after your own validation. There has been no current-release server smoke test.
