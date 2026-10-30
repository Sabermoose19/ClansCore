package dev.clans.core;

import dev.clans.api.ClansApi.*;
import dev.clans.api.DeveloperStyle;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

/** All authorization and state revalidation occur inside the serialized mutation. */
public final class ClanService {
  final Store store;
  volatile Settings settings;
  final Clock clock;
  private final Map<String, Pending> pending = new ConcurrentHashMap<>();

  record Pending(String action, UUID clan, UUID target, long expiry) {}

  public record Actor(UUID id, String name, boolean admin) {}

  public ClanService(Store store, Settings settings, Clock clock) {
    this.store = store;
    this.settings = settings;
    this.clock = clock;
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  static Clan named(Snapshot s, String name) {
    Clan c = s.named(name);
    require(c != null, "Clan not found.");
    return c;
  }

  private void rank(Clan c, Actor a, Rank required) {
    require(c != null, "You have no clan.");
    if (a.admin) return;
    Member m = c.members().get(a.id);
    require(
        m != null && (required == Rank.MEMBER || m.rank() == Rank.LEADER || m.rank() == required),
        "Your clan rank cannot do that.");
  }

  public CompletableFuture<String> execute(
      Actor a, String action, String[] args, UUID target, String targetName) {
    return executeDetailed(a, action, args, target, targetName).thenApply(Outcome::message);
  }

  public record Notice(
      Set<UUID> recipients, String key, UUID otherClanId, String otherClan, String defaultText) {
    public Notice {
      recipients = Set.copyOf(recipients);
    }
  }

  public record Outcome(String message, List<Notice> notices) {
    public Outcome {
      notices = List.copyOf(notices);
    }

    Outcome(String message) {
      this(message, List.of());
    }
  }

  public CompletableFuture<Outcome> executeDetailed(
      Actor a, String action, String[] args, UUID target, String targetName) {
    return store.mutate(
        state -> {
          long now = clock.millis();
          Settings cfg = settings;
          state.invites.removeIf(i -> i.expires() <= now);
          state.peace.removeIf(p -> p.expires() <= now);
          Snapshot s = state.freeze();
          Clan own = s.clan(a.id);
          Clan c = own;
          if (a.admin && !Set.of("remove", "setrank", "confirm", "cancel").contains(action))
            c = named(s, args[0]);
          String value = args.length == 0 ? "" : args[args.length - 1];
          if (action.equals("cancel")) {
            pending.remove(key(a));
            return new Outcome("Pending action cancelled.");
          }
          if (action.equals("confirm")) {
            Pending p = pending.remove(key(a));
            require(p != null && p.expiry > now, "No unexpired confirmation.");
            c = s.clans().get(p.clan);
            rank(c, a, Rank.LEADER);
            if (p.action.equals("transfer")) {
              require(c.members().containsKey(p.target), "Target is no longer in the clan.");
              transfer(state, c, p.target);
            } else disband(state, c);
            return new Outcome("Confirmed " + p.action + " for " + c.name() + ".");
          }
          switch (action) {
            case "create" -> {
              require(own == null, "You already have a clan.");
              cfg.name(value);
              require(s.named(value) == null, "Clan name already exists.");
              UUID id = UUID.randomUUID();
              state.clans.put(
                  id,
                  new Clan(
                      id,
                      value,
                      "",
                      now,
                      0,
                      false,
                      Map.of(a.id, new Member(a.id, a.name, Rank.LEADER)),
                      Set.of()));
              state.invites.removeIf(i -> i.player().equals(a.id));
              return new Outcome(DeveloperStyle.CREATE_RESPONSE.replace("{clan}", value));
            }
            case "join", "accept" -> {
              require(own == null, "You already have a clan.");
              c = named(s, value);
              final UUID clanId = c.id();
              require(
                  action.equals("join")
                      ? c.open()
                      : state.invites.stream()
                          .anyMatch(i -> i.clan().equals(clanId) && i.player().equals(a.id)),
                  action.equals("join")
                      ? "Clan is invite-only. Use /clan accept with an invitation."
                      : "No valid invitation.");
              require(
                  cfg.memberLimit() == 0 || c.members().size() < cfg.memberLimit(),
                  "Clan is full.");
              Map<UUID, Member> ms = new HashMap<>(c.members());
              ms.put(a.id, new Member(a.id, a.name, Rank.MEMBER));
              state.members(c, ms);
              state.invites.removeIf(i -> i.player().equals(a.id));
            }
            case "deny" -> {
              c = named(s, value);
              final UUID id = c.id();
              require(
                  state.invites.removeIf(i -> i.clan().equals(id) && i.player().equals(a.id)),
                  "No invitation from that clan.");
            }
            case "invite", "uninvite" -> {
              rank(c, a, Rank.ELDER);
              require(target != null, "Player must be online or a known clan member.");
              UUID id = c.id();
              if (action.equals("invite")) {
                require(s.clan(target) == null, "Player already has a clan.");
                state.invites.removeIf(i -> i.clan().equals(id) && i.player().equals(target));
                state.invites.add(new Invitation(id, target, now + cfg.inviteMillis()));
              } else
                require(
                    state.invites.removeIf(i -> i.clan().equals(id) && i.player().equals(target)),
                    "Invitation not found.");
            }
            case "leave",
                "kick",
                "remove",
                "promote",
                "demote",
                "setrank",
                "transfer",
                "setleader" -> {
              if (action.equals("remove") || action.equals("setrank")) c = s.clan(target);
              require(c != null, "Player has no clan.");
              UUID id = action.equals("leave") ? a.id : target;
              Member m = c.members().get(id);
              require(m != null, "Player is not in this clan.");
              Map<UUID, Member> ms = new HashMap<>(c.members());
              switch (action) {
                case "leave" -> {
                  require(m.rank() != Rank.LEADER, "Transfer leadership or disband first.");
                  ms.remove(id);
                  state.members(c, ms);
                }
                case "kick", "remove" -> {
                  rank(c, a, Rank.ELDER);
                  require(
                      (a.admin || !id.equals(a.id)) && m.rank() != Rank.LEADER,
                      "Cannot remove a Leader (or yourself with /clan kick).");
                  require(
                      a.admin
                          || c.members().get(a.id).rank() == Rank.LEADER
                          || m.rank() == Rank.MEMBER,
                      "Elders can only kick Members.");
                  ms.remove(id);
                  state.members(c, ms);
                }
                case "transfer" -> {
                  rank(c, a, Rank.LEADER);
                  require(!id.equals(a.id), "Choose another member.");
                  pending.put(key(a), new Pending("transfer", c.id(), id, now + 30000));
                  return new Outcome(
                      "Use /clan confirm within 30 seconds to transfer leadership to "
                          + m.name()
                          + ".");
                }
                case "setleader" -> transfer(state, c, id);
                default -> {
                  rank(c, a, Rank.LEADER);
                  require(m.rank() != Rank.LEADER, "Cannot change a Leader with this command.");
                  Rank next =
                      action.equals("promote")
                          ? Rank.ELDER
                          : action.equals("demote")
                              ? Rank.MEMBER
                              : Rank.valueOf(value.toUpperCase(Locale.ROOT));
                  require(next != Rank.LEADER, "Use setleader.");
                  require(
                      action.equals("setrank") || m.rank() != next,
                      "Player already has that rank.");
                  ms.put(id, new Member(id, m.name(), next));
                  state.members(c, ms);
                }
              }
            }
            case "rename" -> {
              rank(c, a, Rank.LEADER);
              cfg.name(value);
              Clan same = s.named(value);
              require(same == null || same.id().equals(c.id()), "Name already exists.");
              require(
                  a.admin || c.renamed() == 0 || now - c.renamed() >= cfg.renameMillis(),
                  "Rename cooldown has not expired.");
              state.clans.put(
                  c.id(),
                  new Clan(
                      c.id(),
                      value,
                      c.motto(),
                      c.founded(),
                      now,
                      c.open(),
                      c.members(),
                      c.enemies()));
            }
            case "motto", "clearmotto" -> {
              rank(c, a, Rank.ELDER);
              String text =
                  action.equals("clearmotto")
                      ? ""
                      : String.join(" ", a.admin ? Arrays.copyOfRange(args, 1, args.length) : args);
              if (!a.admin && text.equalsIgnoreCase("clear")) text = "";
              cfg.motto(text);
              state.clans.put(
                  c.id(),
                  new Clan(
                      c.id(),
                      c.name(),
                      text,
                      c.founded(),
                      c.renamed(),
                      c.open(),
                      c.members(),
                      c.enemies()));
            }
            case "open", "close" -> {
              rank(c, a, Rank.LEADER);
              state.clans.put(
                  c.id(),
                  new Clan(
                      c.id(),
                      c.name(),
                      c.motto(),
                      c.founded(),
                      c.renamed(),
                      action.equals("open"),
                      c.members(),
                      c.enemies()));
            }
            case "enemy", "peace", "neutral", "peaceaccept", "peacedeny" -> {
              require(!action.equals("neutral") || a.admin, "Use /clan peace.");
              rank(c, a, Rank.LEADER);
              Clan other = named(s, value);
              require(!other.id().equals(c.id()), "Cannot target your clan.");
              UUID id = c.id(), oid = other.id();
              if (action.equals("enemy")) {
                require(!c.enemies().contains(oid), "Clans are already enemies.");
                setEnemy(state, c, other, true);
                state.peace.removeIf(p -> pair(p, id, oid));
              } else if (a.admin) {
                setEnemy(state, c, other, false);
                state.peace.removeIf(p -> pair(p, id, oid));
              } else {
                require(c.enemies().contains(oid), "Clans are not enemies.");
                if (action.equals("peace")) {
                  state.peace.removeIf(p -> p.from().equals(id) && p.to().equals(oid));
                  state.peace.add(new Peace(id, oid, now + cfg.peaceMillis()));
                } else {
                  require(
                      state.peace.removeIf(p -> p.from().equals(oid) && p.to().equals(id)),
                      "No peace request from that clan.");
                  if (action.equals("peaceaccept")) {
                    setEnemy(state, c, other, false);
                    state.peace.removeIf(p -> pair(p, id, oid));
                  }
                }
              }
              if (!a.admin && !action.equals("peacedeny")) {
                String ownKey, otherKey, ownText, otherText;
                if (action.equals("enemy")) {
                  ownKey = "war-sent";
                  otherKey = "war-received";
                  ownText = "You have declared war on {otherclan}!";
                  otherText = "{otherclan} has declared war on your clan!";
                } else if (action.equals("peace")) {
                  ownKey = "peace-sent";
                  otherKey = "peace-received";
                  ownText = "You sent {otherclan} a peace offer.";
                  otherText = "{otherclan} has offered peace with your clan.";
                } else {
                  ownKey = "peace-made";
                  otherKey = "peace-accepted";
                  ownText = "You have made peace with {otherclan}.";
                  otherText = "Your peace treaty with {otherclan} was accepted.";
                }
                return new Outcome(
                    "",
                    List.of(
                        new Notice(c.members().keySet(), ownKey, other.id(), other.name(), ownText),
                        new Notice(
                            other.members().keySet(), otherKey, c.id(), c.name(), otherText)));
              }
            }
            case "disband" -> {
              rank(c, a, Rank.LEADER);
              pending.put(key(a), new Pending("disband", c.id(), null, now + 30000));
              return new Outcome(
                  "Use /"
                      + (a.admin ? "clanadmin" : "clan")
                      + " confirm within 30 seconds to permanently delete "
                      + c.name()
                      + ".");
            }
            default -> throw new IllegalArgumentException("Unknown action.");
          }
          return new Outcome("Completed " + action + (c == null ? "" : " for " + c.name()) + ".");
        });
  }

  String key(Actor a) {
    return (a.admin ? "admin:" : "player:") + a.id;
  }

  String pendingAction(Actor a) {
    Pending p = pending.get(key(a));
    return p == null ? null : p.action;
  }

  static boolean pair(Peace p, UUID a, UUID b) {
    return (p.from().equals(a) && p.to().equals(b)) || (p.from().equals(b) && p.to().equals(a));
  }

  static void transfer(Store.State st, Clan c, UUID target) {
    Map<UUID, Member> ms = new HashMap<>(c.members());
    ms.replaceAll(
        (id, m) ->
            new Member(
                id,
                m.name(),
                id.equals(target) ? Rank.LEADER : m.rank() == Rank.LEADER ? Rank.ELDER : m.rank()));
    st.members(c, ms);
  }

  static void setEnemy(Store.State st, Clan a, Clan b, boolean hostile) {
    Set<UUID> ae = new HashSet<>(a.enemies()), be = new HashSet<>(b.enemies());
    if (hostile) {
      ae.add(b.id());
      be.add(a.id());
    } else {
      ae.remove(b.id());
      be.remove(a.id());
    }
    st.enemies(a, ae);
    st.enemies(b, be);
  }

  static void disband(Store.State st, Clan c) {
    st.clans.remove(c.id());
    for (Clan other : List.copyOf(st.clans.values())) {
      Set<UUID> es = new HashSet<>(other.enemies());
      es.remove(c.id());
      st.enemies(other, es);
    }
    st.invites.removeIf(i -> i.clan().equals(c.id()));
    st.peace.removeIf(p -> p.from().equals(c.id()) || p.to().equals(c.id()));
  }

  public CompletableFuture<Void> remember(UUID id, String name) {
    return store.mutate(
        st -> {
          Clan c = st.freeze().clan(id);
          if (c != null) {
            var ms = new HashMap<>(c.members());
            ms.put(id, new Member(id, name, ms.get(id).rank()));
            st.members(c, ms);
          }
          return null;
        });
  }
}
