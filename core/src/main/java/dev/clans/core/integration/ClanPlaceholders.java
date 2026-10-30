package dev.clans.core.integration;

import dev.clans.api.ClansApi.*;
import dev.clans.api.DeveloperStyle;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Pure cached data; no player/world access, SQL, permissions or network requests. */
public final class ClanPlaceholders {
  private ClanPlaceholders() {}

  public enum Field {
    NAME("name"),
    STARS("stars"),
    PREFIX("chatprefix");
    final String parameter;

    Field(String parameter) {
      this.parameter = parameter;
    }

    public String placeholder() {
      return "%clanscore_" + parameter + "%";
    }

    public static Field parse(String parameter) {
      return Arrays.stream(values())
          .filter(f -> f.parameter.equalsIgnoreCase(parameter))
          .findFirst()
          .orElse(null);
    }
  }

  public static String stars(Rank rank) {
    return switch (rank) {
      case LEADER -> "**";
      case ELDER -> "*";
      case MEMBER -> "";
    };
  }

  public static String plain(Snapshot snapshot, UUID sender, Field field) {
    Clan clan = sender == null ? null : snapshot.clan(sender);
    if (clan == null) return "";
    String stars = stars(clan.members().get(sender).rank());
    return switch (field) {
      case NAME -> clan.name();
      case STARS -> stars;
      case PREFIX -> stars + clan.name() + " ";
    };
  }

  public static Component component(Snapshot snapshot, UUID sender, UUID viewer, Field field) {
    Clan clan = snapshot.clan(sender);
    if (clan == null) return Component.empty();
    Clan own = viewer == null ? null : snapshot.clan(viewer);
    TextColor colour =
        own == null
            ? DeveloperStyle.NEUTRAL_CLAN
            : own.id().equals(clan.id())
                ? DeveloperStyle.SAME_CLAN
                : own.enemies().contains(clan.id())
                    ? DeveloperStyle.ENEMY_CLAN
                    : DeveloperStyle.NEUTRAL_CLAN;
    String stars = stars(clan.members().get(sender).rank());
    return switch (field) {
      case NAME -> Component.text(clan.name(), colour);
      case STARS -> Component.text(stars, colour);
      case PREFIX ->
          Component.text(stars, colour)
              .append(Component.text(clan.name(), colour))
              .append(Component.text(" ", DeveloperStyle.NEUTRAL_CLAN));
    };
  }
}
