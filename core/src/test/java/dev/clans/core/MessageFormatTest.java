package dev.clans.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.clans.api.ClansApi.*;
import java.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MessageFormatTest {
  String plain(Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  @Test
  void yellowSentencesUseLiteralColouredNamesWithoutPrefixOrStars() {
    Component text =
        MessageFormat.relationship(
            "{otherclan} has offered peace with your clan.",
            "Bravo",
            NamedTextColor.RED,
            "Alpha",
            NamedTextColor.GREEN);
    assertEquals("Bravo has offered peace with your clan.", plain(text));
    assertEquals(NamedTextColor.YELLOW, text.color());
    assertTrue(
        nodes(text).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().equals("Bravo")
                        && c.color() == NamedTextColor.RED));
    Component injected =
        MessageFormat.relationship(
            "<yellow>{clan}: {otherclan}</yellow>",
            "<click:run_command:'/op Bob'>Bob</click>",
            NamedTextColor.AQUA,
            "Alpha",
            NamedTextColor.GREEN);
    assertEquals("Alpha: <click:run_command:'/op Bob'>Bob</click>", plain(injected));
    assertTrue(nodes(injected).stream().allMatch(c -> c.clickEvent() == null));
  }

  @Test
  void trustedTemplatesSupportMiniMessageButUserContentStaysLiteral() {
    Component wrapped =
        MessageFormat.wrap("<yellow>{text}</yellow>", Component.text("<red>Hello</red>"));
    assertEquals("<red>Hello</red>", plain(wrapped));
    assertEquals(NamedTextColor.YELLOW, wrapped.color());
  }

  @Test
  void buttonsHaveExactColoursCommandsAndPermissionFiltering() {
    Component buttons = MessageFormat.buttons("Bravo", "[Accept peace]", "[Decline]", true, true);
    assertEquals("[Accept peace]  [Decline]", plain(buttons));
    List<Component> actions = nodes(buttons).stream().filter(c -> c.clickEvent() != null).toList();
    assertEquals(2, actions.size());
    assertEquals(NamedTextColor.GREEN, actions.get(0).color());
    assertEquals(ClickEvent.runCommand("/clan peace accept Bravo"), actions.get(0).clickEvent());
    assertEquals(NamedTextColor.RED, actions.get(1).color());
    assertEquals(ClickEvent.runCommand("/clan peace deny Bravo"), actions.get(1).clickEvent());
    assertEquals(
        "[Decline]",
        plain(MessageFormat.buttons("Bravo", "[Accept peace]", "[Decline]", false, true)));
  }

  @Test
  void onlyReceivingLeaderWithLiveOfferCanRespondAndRelationsChangeAfterPeace() {
    UUID a = UUID.randomUUID(),
        b = UUID.randomUUID(),
        member = UUID.randomUUID(),
        x = UUID.randomUUID(),
        y = UUID.randomUUID();
    Clan alpha =
        new Clan(
            x, "Alpha", "", 0, 0, false, Map.of(a, new Member(a, "Alice", Rank.LEADER)), Set.of(y));
    Clan bravo =
        new Clan(
            y,
            "Bravo",
            "",
            0,
            0,
            false,
            Map.of(
                b,
                new Member(b, "Bob", Rank.LEADER),
                member,
                new Member(member, "Member", Rank.MEMBER)),
            Set.of(x));
    Snapshot s = new Snapshot(Map.of(x, alpha, y, bravo), List.of(), List.of(new Peace(x, y, 100)));
    assertEquals(NamedTextColor.GREEN, MessageFormat.clanColour(s, x, a));
    assertEquals(NamedTextColor.RED, MessageFormat.clanColour(s, x, b));
    assertEquals(NamedTextColor.WHITE, MessageFormat.clanColour(s, x, UUID.randomUUID()));
    assertTrue(MessageFormat.mayRespond(s, b, x, 99));
    assertFalse(MessageFormat.mayRespond(s, member, x, 99));
    assertFalse(MessageFormat.mayRespond(s, a, y, 99));
    assertFalse(MessageFormat.mayRespond(s, b, x, 100));
    Clan peaceful = new Clan(y, "Bravo", "", 0, 0, false, bravo.members(), Set.of());
    Snapshot next = new Snapshot(Map.of(x, alpha, y, peaceful), List.of(), List.of());
    assertEquals(NamedTextColor.WHITE, MessageFormat.clanColour(next, x, b));
    assertFalse(MessageFormat.mayRespond(next, b, x, 99));
  }

  List<Component> nodes(Component c) {
    List<Component> result = new ArrayList<>();
    result.add(c);
    for (Component child : c.children()) result.addAll(nodes(child));
    return result;
  }
}
