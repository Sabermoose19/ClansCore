# Commands and permissions

All ordinary command nodes default to **true**. Administrative nodes default to **op**. Membership and clan rank checks are independent of permission nodes. No node grants clan-chat spying, forced membership, or Leader powers through normal commands.

Root commands declare permissions to the server so inaccessible roots and their plugin-namespaced forms are omitted from client suggestions. `/clanadmin` requires any effective `clanscore.admin.<command>` permission, not the entire wildcard. `/clan` requires any Core player command permission or installed subcommand extension permission. Bukkit's native semicolon-separated permission expressions implement this OR check; individual command checks still apply. There is no additional root-access permission to grant. Normal suggestions also filter clan ranks, and `/clan peace accept|deny` checks each response node. OPs retain their default administrative permissions; use a non-OP account when testing denial. Reconnect if your client retains an outdated command tree after permission edits.

`/clan` is `/clan help`. Help filters commands by the sender's permissions and current membership/rank. Lists are paginated, with explicit page counts and commands to retrieve further members. Console can use help, list, specified-clan info/members, and all meaningful staff commands. Commands that act as a clan member require a player.

`/c` aliases the entire `/clan` root. Private clan chat has been removed: `/cc`, `/clan chat` and `/c chat` are not supplied. `/clan who` and `/c who` alias `/clan info` with the same optional clan/page arguments. Aliases share the canonical command's permissions: `who` uses `clanscore.player.info`, with no separate permission to grant. `/clanadmin` is unchanged.

## Player commands

| Command | Permission | Clan requirement / result |
|---|---|---|
| `/clan`, `/clan help [page]` | `clanscore.player.help` | Show available commands. |
| `/clan create <name>` | `clanscore.player.create` | Unclanned; create invite-only clan, become its one Leader. |
| `/clan info [clan] [page]` | `clanscore.player.info` | Specified clan, or own clan. Name, motto, founding date, policy, totals, online count, enemies, ranked paginated members. Leaders also see incoming unexpired peace requests. |
| `/clan members [clan] [page]` | `clanscore.player.members` | All members grouped Leader/Elder/Member with online/offline markers; 12 rows per page. |
| `/clan list [page]` | `clanscore.player.list` | Names, counts and Open/Invite-only status; 8 rows per page. |
| `/clan invites` | `clanscore.player.invites` | Player's valid incoming invitations and expiry dates. |
| `/clan invite <player>` | `clanscore.player.invite` | Leader/Elder; target must be unclanned. Creates/replaces own clan's invitation. |
| `/clan uninvite <player>` | `clanscore.player.uninvite` | Leader/Elder; revoke own clan's invitation. |
| `/clan accept <clan>` | `clanscore.player.accept` | Unclanned; valid invite required even if the clan has since closed. Enforces member limit; clears all invites for player. |
| `/clan deny <clan>` | `clanscore.player.deny` | Decline incoming invitation. |
| `/clan join <clan>` | `clanscore.player.join` | Unclanned; Open clan only; same member limit as accept. |
| `/clan leave` | `clanscore.player.leave` | Member/Elder. Leader must transfer or disband. |
| `/clan kick <player>` | `clanscore.player.kick` | Elder can kick Members; Leader can kick Members/Elders. Cannot kick self or Leader. |
| `/clan promote <player>` | `clanscore.player.promote` | Leader; existing Member becomes Elder. |
| `/clan demote <player>` | `clanscore.player.demote` | Leader; existing Elder becomes Member. |
| `/clan transfer <player>` | `clanscore.player.transfer` | Leader; existing other member; requires confirm. Old Leader becomes Elder atomically. |
| `/clan rename <name>` | `clanscore.player.rename` | Leader; validates name/uniqueness/cooldown; preserves ID, founding date, members, relations, invites. |
| `/clan motto <text...>`, `/clan motto clear` | `clanscore.player.motto` | Leader/Elder; plain-text motto, or remove it. |
| `/clan open` | `clanscore.player.open` | Leader; permit direct joining. |
| `/clan close` | `clanscore.player.close` | Leader; restore invite-only; existing invites remain valid. |
| `/clan enemy <clan>` | `clanscore.player.enemy` | Leader; mutual Enemy relationship. Cannot target own clan. |
| `/clan peace <clan>` | `clanscore.player.peace` | Leader; request peace from enemy. Hostility remains. |
| `/clan peace accept <clan>` | `clanscore.player.peace.accept` | Receiving Leader; valid incoming peace request required. Clears hostility both ways. |
| `/clan peace deny <clan>` | `clanscore.player.peace.deny` | Receiving Leader; removes incoming request; hostility remains. |
| `/clan disband` | `clanscore.player.disband` | Leader; request permanent deletion; requires confirm. |
| `/clan confirm` | `clanscore.player.confirm` | Confirm latest transfer/disband within 30 seconds. Also requires original transfer/disband permission, current Leader status, valid clan/target. |
| `/clan cancel` | `clanscore.player.cancel` | Cancel own pending normal action. |

Named targets resolve from players seen during this run, stored clan members, or the server's cached offline profiles; no blocking Mojang lookup or fabricated offline UUID is used. For an unknown target, ask them to log in first. This avoids accidentally inviting the wrong UUID on online-mode servers. Online/offline names remain display/lookup hints; memberships are UUID-based.

For a numeric clan name, `/clan info 123` resolves the existing clan `123`. Use the explicit clan and page to remove ambiguity (`/clan members 123 2`). Bare page numbers are accepted for one's own member/info pages only when there is no clan with that numeric name.

## Staff commands

| Command | Permission | Result |
|---|---|---|
| `/clanadmin help [page]` | `clanscore.admin.help` | Paginated staff command list filtered by permission. |
| `/clanadmin info <clan> [page]` | `clanscore.admin.info` | Clan details plus persistent internal UUID. |
| `/clanadmin rename <clan> <name>` | `clanscore.admin.rename` | Bypass cooldown; still validates names and uniqueness. |
| `/clanadmin motto <clan> <text...>` | `clanscore.admin.motto` | Replace motto; same plain-text validation/length limit. |
| `/clanadmin clearmotto <clan>` | `clanscore.admin.clearmotto` | Remove motto. |
| `/clanadmin remove <player>` | `clanscore.admin.remove` | Remove Member/Elder; refuses Leader. |
| `/clanadmin setrank <player> <member\|elder>` | `clanscore.admin.setrank` | Existing Member/Elder only. Cannot demote/replace Leader. |
| `/clanadmin setleader <clan> <player>` | `clanscore.admin.setleader` | Existing member of specified clan becomes Leader; old Leader becomes Elder. |
| `/clanadmin open <clan>` | `clanscore.admin.open` | Enable Open policy. |
| `/clanadmin close <clan>` | `clanscore.admin.close` | Enable Invite-only policy. |
| `/clanadmin neutral <clanA> <clanB>` | `clanscore.admin.neutral` | Immediately remove mutual hostility and all pending peace requests for pair. |
| `/clanadmin disband <clan>` | `clanscore.admin.disband` | Request permanent deletion. |
| `/clanadmin confirm` | `clanscore.admin.confirm` | Confirm own admin deletion within 30 seconds; also rechecks `clanscore.admin.disband`. |
| `/clanadmin cancel` | `clanscore.admin.cancel` | Cancel own admin action. |
| `/clanadmin reload` | `clanscore.admin.reload` | Reload supported Core runtime settings; source formatting requires rebuild/restart. |

Administrative changes are logged to console with actor, action, arguments/target and result. Admin disband confirmations are independent of normal confirmations. There is no staff chat-spy or forced clan membership command. Staff commands do not bypass name/motto validation, membership identity, or the one-Leader invariant.

## Explicit wildcard relationships

| Node | Default | Explicit children |
|---|---|---|
| `clanscore.player.*` | true | Every `clanscore.player.<command>` above, including `peace.accept`, `peace.deny`; no private-chat permissions are supplied. |
| `clanscore.admin.*` | op | Every `clanscore.admin.<command>` above. No clan membership is granted. |
| `clanscore.*` | op | `clanscore.player.*`, `clanscore.admin.*`. |

Core plugin.yml declares its wildcard/administrative relationships explicitly. There is no ClansChat plugin, player chat command or private-chat permission node in this release. LPC commands/permissions belong to LPC. Core's extension mechanism remains available to other integrations. It does not rely on a permissions plugin interpreting `*` patterns. Child denials can be used to restrict individual commands. Private-chat commands are absent from Core help in this release.
