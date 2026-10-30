package dev.clans.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.clans.api.ClansApi.*;
import dev.clans.api.DeveloperStyle;
import java.util.*;
import java.util.stream.IntStream;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class DeveloperStyleTest {
  @Test
  void boxedInfoUsesClanHeaderAndPluginSpecificValues() {
    Component header =
        DeveloperStyle.render(
            DeveloperStyle.INFO_LINES.getFirst(),
            Map.of("clan", Component.text("Alpha", NamedTextColor.GREEN)));
    assertEquals("---------------[ Alpha ]---------------", plain(header));
    assertTrue(
        nodes(header).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().equals("Alpha")
                        && c.color() == NamedTextColor.GREEN));
    Component empty =
        DeveloperStyle.render(
            DeveloperStyle.MEMBER_GROUP_HEADER,
            Map.of(
                "rank",
                Component.text("Elders"),
                "count",
                DeveloperStyle.value(0),
                "empty",
                DeveloperStyle.value("None")));
    assertEquals("Elders (0): None", plain(empty));
  }

  @Test
  void rankSectionHeadersDoNotConsumeMemberSlotsOrDropMembersAcrossPages() {
    List<Member> members = new ArrayList<>();
    members.add(new Member(UUID.randomUUID(), "Leader", Rank.LEADER));
    members.add(new Member(UUID.randomUUID(), "Elder", Rank.ELDER));
    for (int i = 0; i < 24; i++)
      members.add(new Member(UUID.randomUUID(), "Member" + i, Rank.MEMBER));
    List<Member> collected = new ArrayList<>();
    for (int page = 1; page <= 3; page++)
      for (var group : DeveloperStyle.memberSections(members, page, 12)) {
        collected.addAll(group.rows());
        assertEquals(members.stream().filter(m -> m.rank() == group.rank()).count(), group.total());
      }
    assertEquals(members, collected);
    var solo = DeveloperStyle.memberSections(members.subList(0, 1), 1, 12);
    assertEquals(
        List.of(Rank.LEADER, Rank.ELDER, Rank.MEMBER),
        solo.stream().map(DeveloperStyle.MemberSection::rank).toList());
    assertTrue(solo.get(1).rows().isEmpty());
  }

  @Test
  void emptyPeaceOffersStayOnOneLineWithYellowLabelAndAquaNone() {
    Component line = DeveloperStyle.peaceHeader(true);
    assertEquals("Incoming peace offers: None", plain(line));
    assertFalse(plain(line).contains("\n"));
    assertTrue(
        nodes(line).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().contains("Incoming peace offers:")
                        && c.color() == NamedTextColor.YELLOW));
    assertTrue(
        nodes(line).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().equals("None")
                        && c.color() == NamedTextColor.AQUA));
    assertEquals("Incoming peace offers: ", plain(DeveloperStyle.peaceHeader(false)));
  }

  @Test
  void displayedDataValuesKeepAquaEvenInsideYellowSentences() {
    for (Object value : List.of(1, "2026-10-29 22:05 UTC", "Not set", "None", "Invite-only")) {
      Component data = ClansCore.text(value);
      assertEquals(NamedTextColor.AQUA, data.color());
      Component line =
          DeveloperStyle.render("<yellow>Value: {data}</yellow>", Map.of("data", data));
      assertEquals("Value: " + value, plain(line));
      assertTrue(
          nodes(line).stream()
              .anyMatch(
                  c ->
                      c instanceof TextComponent t
                          && t.content().equals(String.valueOf(value))
                          && c.color() == NamedTextColor.AQUA));
    }
  }

  String plain(Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  List<Component> nodes(Component c) {
    List<Component> out = new ArrayList<>();
    out.add(c);
    for (Component child : c.children()) out.addAll(nodes(child));
    return out;
  }

  @Test
  void helpUsageIsLiteralAndSourceTemplateControlsLayout() {
    Component row =
        DeveloperStyle.render(
            DeveloperStyle.HELP_ROW,
            Map.of("command", Component.text("/clan create"), "usage", Component.text("<name>")));
    assertEquals("/clan create <name>", plain(row));
    assertEquals(NamedTextColor.YELLOW, row.color());
    Component reordered =
        DeveloperStyle.render(
            "<yellow>{usage}</yellow> — <aqua>{command}</aqua>",
            Map.of("command", Component.text("/clan create"), "usage", Component.text("<name>")));
    assertEquals("<name> — /clan create", plain(reordered));
    Component footer =
        DeveloperStyle.render(DeveloperStyle.HELP_FOOTER, Map.of("root", Component.text("clan")));
    assertEquals("Pages: /clan help <page>", plain(footer));
  }

  @Test
  void infoValuesCannotInjectFormattingAndClanColoursArePreserved() {
    String malicious = "<click:run_command:'/op Bob'>Hello</click>";
    Map<String, Component> values =
        Map.of(
            "clan",
            Component.text("Alpha", NamedTextColor.GREEN),
            "motto",
            Component.text(malicious),
            "leader",
            Component.text("Alice", NamedTextColor.AQUA));
    Component line =
        DeveloperStyle.render("<aqua>Motto:</aqua> {motto} — {clan} — {leader}", values);
    assertEquals("Motto: " + malicious + " — Alpha — Alice", plain(line));
    assertTrue(nodes(line).stream().allMatch(c -> c.clickEvent() == null));
    assertTrue(
        nodes(line).stream()
            .anyMatch(
                c ->
                    c instanceof TextComponent t
                        && t.content().equals("Alpha")
                        && c.color() == NamedTextColor.GREEN));
    assertFalse(plain(line).contains("**"));
  }

  @Test
  void memberPaginationKeepsEveryMemberAndRejectsInvalidPages() {
    List<Integer> members = IntStream.range(0, 31).boxed().toList();
    List<Integer> collected = new ArrayList<>();
    for (int page = 1; page <= 3; page++) {
      var selected = DeveloperStyle.page(members, page, 12);
      assertEquals(3, selected.pages());
      collected.addAll(selected.rows());
    }
    assertEquals(members, collected);
    assertEquals(1, DeveloperStyle.page(List.of(), 1, 12).pages());
    assertThrows(IllegalArgumentException.class, () -> DeveloperStyle.page(members, 0, 12));
    assertThrows(IllegalArgumentException.class, () -> DeveloperStyle.page(members, 4, 12));
    assertThrows(IllegalArgumentException.class, () -> DeveloperStyle.page(members, 1, 0));
    assertThrows(
        UnsupportedOperationException.class,
        () -> DeveloperStyle.page(members, 1, 12).rows().clear());
  }

  @Test
  void everyStructuredLayoutHasResolvableSafePlaceholders() {
    Map<String, Component> values = new HashMap<>();
    for (String key :
        List.of(
            "root",
            "command",
            "usage",
            "title",
            "page",
            "pages",
            "clan",
            "id",
            "motto",
            "founded",
            "policy",
            "leader",
            "total",
            "online",
            "offline",
            "enemy-count",
            "enemies",
            "rank",
            "username",
            "status",
            "expires",
            "offers")) values.put(key, Component.text("value"));
    values.put("count", Component.text("value"));
    values.put("empty", Component.text("value"));
    List<String> templates = new ArrayList<>(DeveloperStyle.INFO_LINES);
    templates.addAll(DeveloperStyle.ADMIN_INFO_EXTRA);
    templates.addAll(
        List.of(
            DeveloperStyle.HELP_HEADER,
            DeveloperStyle.HELP_ROW,
            DeveloperStyle.HELP_FOOTER,
            DeveloperStyle.MEMBERS_HEADER,
            DeveloperStyle.MEMBER_ROW,
            DeveloperStyle.MEMBER_GROUP_HEADER,
            DeveloperStyle.MEMBERS_FOOTER,
            DeveloperStyle.LIST_HEADER,
            DeveloperStyle.LIST_ROW,
            DeveloperStyle.LIST_FOOTER,
            DeveloperStyle.INVITES_HEADER,
            DeveloperStyle.INVITE_ROW,
            DeveloperStyle.INVITES_FOOTER,
            DeveloperStyle.PEACE_HEADER,
            DeveloperStyle.PEACE_ROW));
    for (String template : templates) {
      String output = plain(DeveloperStyle.render(template, values));
      assertFalse(output.contains("{"), template);
      assertFalse(output.contains("dev_"), template);
      assertFalse(output.contains("&lt;"), template);
      assertFalse(output.contains("\\<"), template);
    }
  }
}
