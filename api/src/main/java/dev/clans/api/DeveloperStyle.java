package dev.clans.api;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

/**
 * EDIT THIS FILE FOR CLANSCORE FORMATTING BEFORE COMPILING. These layouts and colours are compiled
 * into ClansCore's shared API; runtime YAML message/format keys cannot override them. All inserted
 * values are literal components, so names, mottos and messages cannot inject formatting.
 */
public final class DeveloperStyle {
  private DeveloperStyle() {}

  public static final TextColor SYSTEM = NamedTextColor.YELLOW;
  public static final TextColor VALUE = NamedTextColor.AQUA;
  public static final TextColor PLAYER_NAME = NamedTextColor.AQUA;
  public static final TextColor SAME_CLAN = NamedTextColor.GREEN;
  public static final TextColor ENEMY_CLAN = NamedTextColor.RED;
  public static final TextColor NEUTRAL_CLAN = NamedTextColor.WHITE;
  public static final TextColor ACCEPT = NamedTextColor.GREEN;
  public static final TextColor DECLINE = NamedTextColor.RED;
  public static final String CREATE_RESPONSE = "You have created {clan}";

  // Core system-message wrappers and relationship sentences (MiniMessage).
  public static final Map<String, String> CORE_MESSAGES =
      Map.ofEntries(
          Map.entry("success", "<yellow>{text}</yellow>"),
          Map.entry("error", "<red>Error:</red> <yellow>{text}</yellow>"),
          Map.entry("line", "{text}"),
          Map.entry("notification", "<yellow>{text}</yellow>"),
          Map.entry("war-received", "{otherclan} has declared war on your clan!"),
          Map.entry("war-sent", "You have declared war on {otherclan}!"),
          Map.entry("peace-received", "{otherclan} has offered peace with your clan."),
          Map.entry("peace-sent", "You sent {otherclan} a peace offer."),
          Map.entry("peace-made", "You have made peace with {otherclan}."),
          Map.entry("peace-accepted", "Your peace treaty with {otherclan} was accepted."),
          Map.entry("peace-accept-button", "[Accept peace]"),
          Map.entry("peace-decline-button", "[Decline]"));
  public static final String CORE_RELOADED = "Runtime settings reloaded.";
  public static final String INTEGRATION_RESTART_REQUIRED =
      "LPC/PlaceholderAPI hook settings changed; a full server restart is required to apply them.";

  // Ordered Java regex rewrites for generated command/error wording, before
  // inserting the result as literal {text}. Example: Map.entry("^You have no clan\\.$", "Create or
  // join a clan first.")
  public static final List<Map.Entry<String, String>> RESPONSE_REPLACEMENTS = List.of();

  // Help: {root}, {command}, {usage}, {page}, {pages}.
  public static final int HELP_PAGE_SIZE = 8;
  public static final String HELP_TITLE = "Clan help";
  public static final String ADMIN_HELP_TITLE = "Clan staff help";
  public static final String HELP_HEADER =
      "<yellow><bold>{title}</bold></yellow> <aqua>({page}/{pages})</aqua>";
  public static final String HELP_ROW = "<aqua>{command}</aqua> <yellow>{usage}</yellow>";
  public static final String HELP_FOOTER =
      "<yellow>Pages:</yellow> <aqua>/{root} help \\<page></aqua>";

  // Info: reorder/delete/add lines freely. All fields listed below are supplied
  // as safe placeholders. {clan}/{enemies} keep per-viewer relationship colours.
  public static final List<String> INFO_LINES =
      List.of(
          "<yellow>---------------[ </yellow>{clan}<yellow> ]---------------</yellow>",
          "<yellow>Motto:</yellow> {motto}",
          "<yellow>Founded:</yellow> {founded}",
          "<yellow>Membership:</yellow> {policy}",
          "<yellow>Leader:</yellow> {leader}",
          "<yellow>Members:</yellow> {total} <gold>|</gold> <yellow>Online:</yellow>"
              + " {online} <gold>|</gold> <yellow>Offline:</yellow> {offline}",
          "<yellow>Enemies (</yellow>{enemy-count}<yellow>):</yellow> {enemies}");
  public static final List<String> ADMIN_INFO_EXTRA = List.of("<yellow>Internal ID:</yellow> {id}");
  public static final String PEACE_HEADER = "<yellow>Incoming peace offers:</yellow> {offers}";
  public static final String PEACE_ROW = "  {clan} <yellow>— expires {expires}</yellow>";

  // Ranked member pages: {rank}, {username}, {status}; page fields and {clan}.
  public static final int MEMBER_PAGE_SIZE = 12;
  public static final String MEMBERS_HEADER =
      "<yellow>Roster</yellow> <gold>[ </gold>{clan}<gold> ]</gold> <yellow>Page</yellow>"
          + " {page}<gold>/</gold>{pages}";
  public static final String MEMBER_GROUP_HEADER =
      "<yellow>{rank} (</yellow>{count}<yellow>):</yellow> {empty}";
  public static final String MEMBER_ROW =
      "<gold> - </gold>{username}" + " <dark_gray>[</dark_gray>{status}<dark_gray>]</dark_gray>";
  public static final String MEMBERS_FOOTER =
      "<yellow>Pages:</yellow> <aqua>{command}"
          + " \\<page></aqua><newline><yellow>-------------------------------------------</yellow>";
  public static final Map<ClansApi.Rank, String> RANK_SECTION_NAMES =
      Map.of(
          ClansApi.Rank.LEADER,
          "Leader",
          ClansApi.Rank.ELDER,
          "Elders",
          ClansApi.Rank.MEMBER,
          "Members");
  public static final Map<ClansApi.Rank, String> RANK_NAMES =
      Map.of(
          ClansApi.Rank.LEADER,
          "Leader",
          ClansApi.Rank.ELDER,
          "Elder",
          ClansApi.Rank.MEMBER,
          "Member");
  public static final String ONLINE = "<aqua>online</aqua>";
  public static final String OFFLINE = "<aqua>offline</aqua>";

  // Clan list and invitations: {clan}, {total}, {policy}, {expires}.
  public static final int LIST_PAGE_SIZE = 8;
  public static final String LIST_HEADER =
      "<yellow><bold>Clans</bold></yellow> <aqua>({page}/{pages})</aqua>";
  public static final String LIST_ROW =
      "{clan} <dark_gray>—</dark_gray> <aqua>Members:</aqua> {total} <dark_gray>—</dark_gray>"
          + " {policy}";
  public static final String LIST_FOOTER =
      "<yellow>Pages:</yellow> <aqua>/clan list \\<page></aqua>";
  public static final String INVITES_HEADER = "<yellow><bold>Clan invitations</bold></yellow>";
  public static final String INVITE_ROW = "{clan} <yellow>— expires {expires}</yellow>";
  public static final String INVITES_FOOTER =
      "<yellow>Respond:</yellow> <aqua>/clan accept \\<clan></aqua> <yellow>or</yellow> <aqua>/clan"
          + " deny \\<clan></aqua>";
  public static final String NO_INVITES = "No pending invitations.";
  public static final String NONE = "None";
  public static final String NO_MOTTO = "Not set";
  public static final String OPEN = "Open";
  public static final String INVITE_ONLY = "Invite-only";
  public static final String CLAN_SEPARATOR = "<yellow>, </yellow>";

  public static Component value(Object value) {
    return Component.text(String.valueOf(value), VALUE);
  }

  public static Component peaceHeader(boolean empty) {
    return render(PEACE_HEADER, Map.of("offers", empty ? value(NONE) : Component.empty()));
  }

  /** MiniMessage parses developer templates only; placeholder content is never reparsed. */
  public static Component render(String template, Map<String, Component> values) {
    List<TagResolver> resolvers = new ArrayList<>();
    String prepared = template;
    for (var entry : values.entrySet()) {
      String tag = "dev_" + entry.getKey().replace('-', '_');
      prepared = prepared.replace("{" + entry.getKey() + "}", "<" + tag + ">");
      resolvers.add(Placeholder.component(tag, entry.getValue()));
    }
    return MiniMessage.miniMessage()
        .deserialize(prepared, TagResolver.resolver(resolvers))
        .colorIfAbsent(SYSTEM);
  }

  public record Page<T>(List<T> rows, int number, int pages) {
    public Page {
      rows = List.copyOf(rows);
    }
  }

  public record MemberSection(ClansApi.Rank rank, int total, List<ClansApi.Member> rows) {
    public MemberSection {
      rows = List.copyOf(rows);
    }
  }

  /**
   * Select members before adding section headers; never paginate or drop member rows as headings.
   */
  public static List<MemberSection> memberSections(
      List<ClansApi.Member> ordered, int number, int size) {
    var selected = page(ordered, number, size);
    List<MemberSection> result = new ArrayList<>();
    for (var rank : ClansApi.Rank.values()) {
      int total = (int) ordered.stream().filter(m -> m.rank() == rank).count();
      var rows = selected.rows().stream().filter(m -> m.rank() == rank).toList();
      if (!rows.isEmpty() || (total == 0 && number == 1))
        result.add(new MemberSection(rank, total, rows));
    }
    return List.copyOf(result);
  }

  public static <T> Page<T> page(List<T> rows, int number, int size) {
    if (size < 1) throw new IllegalArgumentException("Page size must be positive.");
    int pages = Math.max(1, (rows.size() + size - 1) / size);
    if (number < 1 || number > pages)
      throw new IllegalArgumentException("Page out of range (1–" + pages + ").");
    return new Page<>(
        rows.subList(
            Math.min((number - 1) * size, rows.size()), Math.min(number * size, rows.size())),
        number,
        pages);
  }
}
