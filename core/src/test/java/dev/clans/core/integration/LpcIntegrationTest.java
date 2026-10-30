package dev.clans.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import dev.clans.api.ClansApi;
import dev.clans.api.ClansApi.*;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.clip.placeholderapi.replacer.CharsReplacer;
import me.clip.placeholderapi.replacer.Replacer.Closure;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LpcIntegrationTest {
  UUID alice = UUID.randomUUID(),
      elder = UUID.randomUUID(),
      member = UUID.randomUUID(),
      bob = UUID.randomUUID(),
      dan = UUID.randomUUID();
  UUID alpha = UUID.randomUUID(), bravo = UUID.randomUUID(), delta = UUID.randomUUID();

  Snapshot snapshot() {
    return new Snapshot(
        Map.of(
            alpha,
                new Clan(
                    alpha,
                    "Alpha",
                    "",
                    0,
                    0,
                    false,
                    Map.of(
                        alice,
                        new Member(alice, "Alice", Rank.LEADER),
                        elder,
                        new Member(elder, "Elder", Rank.ELDER),
                        member,
                        new Member(member, "Member", Rank.MEMBER)),
                    Set.of(bravo)),
            bravo,
                new Clan(
                    bravo,
                    "Bravo",
                    "",
                    0,
                    0,
                    false,
                    Map.of(bob, new Member(bob, "Bob", Rank.LEADER)),
                    Set.of(alpha)),
            delta,
                new Clan(
                    delta,
                    "Delta",
                    "",
                    0,
                    0,
                    false,
                    Map.of(dan, new Member(dan, "Dan", Rank.LEADER)),
                    Set.of())),
        List.of(),
        List.of());
  }

  static final class Api implements ClansApi {
    final AtomicReference<Snapshot> value;

    Api(Snapshot value) {
      this.value = new AtomicReference<>(value);
    }

    public Snapshot snapshot() {
      return value.get();
    }

    public AutoCloseable subscribe(Consumer<Snapshot> l) {
      return () -> {};
    }

    public AutoCloseable registerExtension(String n, String p, String u, Extension e) {
      throw new UnsupportedOperationException();
    }
  }

  Player player(UUID id) {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (p, m, args) ->
                switch (m.getName()) {
                  case "getUniqueId" -> id;
                  case "getName" -> "Alice";
                  case "equals" -> p == args[0];
                  case "hashCode" -> id.hashCode();
                  case "toString" -> id.toString();
                  default -> m.getReturnType() == boolean.class ? false : null;
                });
  }

  String plain(Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  List<Component> nodes(Component c) {
    List<Component> result = new ArrayList<>();
    result.add(c);
    for (var child : c.children()) result.addAll(nodes(child));
    return result;
  }

  Component text(String s) {
    return Component.text(s);
  }

  ChatRenderer lpc(String prefix) {
    return (source, display, message, viewer) ->
        Component.text("<" + prefix + "[Owner] Alice> ", NamedTextColor.WHITE).append(message);
  }

  @Test
  void ordinaryPlaceholderValuesSupportEveryRankAndNoClan() {
    var s = snapshot();
    assertEquals("Alpha", ClanPlaceholders.plain(s, alice, ClanPlaceholders.Field.NAME));
    assertEquals("**", ClanPlaceholders.plain(s, alice, ClanPlaceholders.Field.STARS));
    assertEquals("**Alpha ", ClanPlaceholders.plain(s, alice, ClanPlaceholders.Field.PREFIX));
    assertEquals("*Alpha ", ClanPlaceholders.plain(s, elder, ClanPlaceholders.Field.PREFIX));
    assertEquals("Alpha ", ClanPlaceholders.plain(s, member, ClanPlaceholders.Field.PREFIX));
    for (var field : ClanPlaceholders.Field.values()) {
      assertEquals("", ClanPlaceholders.plain(s, UUID.randomUUID(), field));
      assertEquals("", ClanPlaceholders.plain(s, null, field));
    }
    assertNull(ClanPlaceholders.Field.parse("unsupported"));
  }

  @Test
  void exactShortAliasColoursClanAndStarsForEachViewer() {
    var scopes = new LpcScope();
    Object event = new Object();
    scopes.begin(event, alice);
    var context = scopes.end(event);
    var renderer = LpcRenderer.wrap(lpc("%clanname%"), snapshot(), context);
    for (var pair :
        Map.of(
                alice,
                NamedTextColor.GREEN,
                bob,
                NamedTextColor.RED,
                dan,
                NamedTextColor.WHITE,
                UUID.randomUUID(),
                NamedTextColor.WHITE)
            .entrySet()) {
      var out = renderer.render(player(alice), text("Alice"), text("Hello"), player(pair.getKey()));
      assertEquals("<**Alpha [Owner] Alice> Hello", plain(out));
      assertTrue(
          nodes(out).stream()
              .anyMatch(
                  c ->
                      c instanceof TextComponent t
                          && t.content().contains("Alpha")
                          && c.color() == pair.getValue()));
    }
    assertEquals(
        "<**Alpha [Owner] Alice> Hello",
        plain(renderer.render(player(alice), text("Alice"), text("Hello"), Audience.empty())));
  }

  @Test
  void playerMessageTokensAreNeverExpandedOrRecoloured() {
    LpcScope scopes = new LpcScope();
    Object event = new Object();
    scopes.begin(event, alice);
    var context = scopes.end(event);
    String input = "%clanname% %clanscore_name% %clanscore_stars% %clanscore_chatprefix%";
    Component message = Component.text(input, NamedTextColor.GOLD);
    var out =
        LpcRenderer.wrap(lpc("%clanname%"), snapshot(), context)
            .render(player(alice), text("Alice"), message, player(bob));
    assertEquals("<**Alpha [Owner] Alice> " + input, plain(out));
    assertTrue(
        nodes(out).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().equals(input)
                        && c.color() == NamedTextColor.GOLD));
  }

  @Test
  void placeholderApiTokensAreDeferredOnlyInsideTheMatchingChatScope() {
    var scopes = new LpcScope();
    var expansion = new ClansExpansion(new Api(snapshot()), scopes, "test");
    var replacer = new CharsReplacer(Closure.PERCENT);
    String format = "%clanname%|%clanscore_name%|%clanscore_stars%|%clanscore_chatprefix%";
    assertEquals(
        "%clanname%|Alpha|**|**Alpha ",
        replacer.apply(format, player(alice), id -> id.equals("clanscore") ? expansion : null));
    Object event = new Object();
    scopes.begin(event, alice);
    String deferred = replacer.apply("%clanscore_chatprefix%", player(alice), id -> expansion);
    assertEquals(scopes.token(alice, ClanPlaceholders.Field.PREFIX), deferred);
    assertEquals("Bravo", expansion.onRequest(player(bob), "name"));
    var context = scopes.end(event);
    assertNull(scopes.token(alice, ClanPlaceholders.Field.PREFIX));
    assertEquals(
        "<**Alpha [Owner] Alice> Hello",
        plain(
            LpcRenderer.wrap(lpc(deferred), snapshot(), context)
                .render(player(alice), text("Alice"), text("Hello"), player(bob))));
    assertTrue(expansion.persist());
    assertNull(expansion.onRequest(player(alice), "unknown"));
    assertEquals("", expansion.onRequest(null, "name"));
  }

  @Test
  void noClanLeavesNoExtraSpaceAndPreservesLpcFormatting() {
    UUID outsider = UUID.randomUUID();
    var scopes = new LpcScope();
    Object event = new Object();
    scopes.begin(event, outsider);
    var context = scopes.end(event);
    var out =
        LpcRenderer.wrap(lpc("%clanname%"), snapshot(), context)
            .render(player(outsider), text("Alice"), text("Hello"), player(alice));
    assertEquals("<[Owner] Alice> Hello", plain(out));
  }

  @Test
  void scopeIsNestedAndThreadIsolated() throws Exception {
    LpcScope scopes = new LpcScope();
    Object outer = new Object(), inner = new Object();
    scopes.begin(outer, alice);
    String before = scopes.token(alice, ClanPlaceholders.Field.NAME);
    scopes.begin(inner, bob);
    assertNull(scopes.token(alice, ClanPlaceholders.Field.NAME));
    assertNotNull(scopes.token(bob, ClanPlaceholders.Field.NAME));
    assertNull(scopes.end(outer));
    scopes.end(inner);
    assertEquals(before, scopes.token(alice, ClanPlaceholders.Field.NAME));
    try (var executor = Executors.newSingleThreadExecutor()) {
      assertNull(executor.submit(() -> scopes.token(alice, ClanPlaceholders.Field.NAME)).get());
    }
    scopes.end(outer);
    assertNull(scopes.token(alice, ClanPlaceholders.Field.NAME));
  }

  AsyncChatEvent event(Player player, Set<Audience> viewers, ChatRenderer renderer) {
    return new AsyncChatEvent(
        true,
        player,
        viewers,
        renderer,
        text("hello"),
        text("hello"),
        SignedMessage.system("hello", text("hello")));
  }

  @Test
  void cancelledEventClearsScopeWithoutChangingRendererOrAudience() {
    var api = new Api(snapshot());
    var scopes = new LpcScope();
    var listener = new LpcAdapter(api, scopes);
    var renderer = lpc("%clanname%");
    Set<Audience> audience = new HashSet<>(Set.of(player(alice), player(bob)));
    var event = event(player(alice), audience, renderer);
    listener.begin(event);
    event.setCancelled(true);
    listener.finish(event);
    assertTrue(event.isCancelled());
    assertSame(renderer, event.renderer());
    assertEquals(audience, event.viewers());
    assertNull(scopes.token(alice, ClanPlaceholders.Field.NAME));
  }

  @Test
  void adapterPreservesAudienceMessageAndCancellationState() {
    var api = new Api(snapshot());
    var scopes = new LpcScope();
    var listener = new LpcAdapter(api, scopes);
    Set<Audience> viewers = new HashSet<>(Set.of(player(alice), player(bob)));
    var event = event(player(alice), viewers, lpc("%clanname%"));
    listener.begin(event);
    listener.finish(event);
    assertFalse(event.isCancelled());
    assertEquals(viewers, event.viewers());
    assertEquals("hello", plain(event.message()));
    assertEquals(
        "<**Alpha [Owner] Alice> hello",
        plain(event.renderer().render(player(alice), text("Alice"), event.message(), player(bob))));
  }

  @Test
  void placeholderLookupsSeeCommittedRenameAndMembershipChanges() {
    var api = new Api(snapshot());
    var expansion = new ClansExpansion(api, new LpcScope(), "test");
    assertEquals("Alpha", expansion.onRequest(player(alice), "name"));
    Map<UUID, Clan> clans = new HashMap<>(api.snapshot().clans());
    Clan previous = clans.get(alpha);
    clans.put(
        alpha, new Clan(alpha, "Renamed", "", 0, 0, false, previous.members(), previous.enemies()));
    api.value.set(new Snapshot(clans, List.of(), List.of()));
    assertEquals("**Renamed ", expansion.onRequest(player(alice), "chatprefix"));
    clans.remove(alpha);
    api.value.set(new Snapshot(clans, List.of(), List.of()));
    assertEquals("", expansion.onRequest(player(alice), "chatprefix"));
  }
}
