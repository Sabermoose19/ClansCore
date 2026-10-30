package dev.clans.core;

import dev.clans.api.ClansApi.*;
import dev.clans.api.DeveloperStyle;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Only administrator-owned templates are parsed; inserted content stays literal. */
final class MessageFormat {
  private static final MiniMessage MM = MiniMessage.miniMessage();

  static Component wrap(String template, Component text) {
    return MM.deserialize(
        template.replace("{text}", "<message_content>"),
        Placeholder.component("message_content", text));
  }

  static Component relationship(
      String template,
      String otherClan,
      TextColor otherColour,
      String ownClan,
      TextColor ownColour) {
    return MM.deserialize(
            template
                .replace("{otherclan}", "<other_clan_name>")
                .replace("{clan}", "<own_clan_name>"),
            Placeholder.component("other_clan_name", Component.text(otherClan, otherColour)),
            Placeholder.component("own_clan_name", Component.text(ownClan, ownColour)))
        .colorIfAbsent(DeveloperStyle.SYSTEM);
  }

  static TextColor clanColour(Snapshot snapshot, UUID clanId, UUID viewer) {
    Clan own = snapshot.clan(viewer);
    if (own == null || clanId == null) return DeveloperStyle.NEUTRAL_CLAN;
    if (own.id().equals(clanId)) return DeveloperStyle.SAME_CLAN;
    return own.enemies().contains(clanId) ? DeveloperStyle.ENEMY_CLAN : DeveloperStyle.NEUTRAL_CLAN;
  }

  static boolean mayRespond(Snapshot snapshot, UUID viewer, UUID offeringClan, long now) {
    Clan own = snapshot.clan(viewer);
    return own != null
        && own.members().get(viewer).rank() == Rank.LEADER
        && own.enemies().contains(offeringClan)
        && snapshot.peace().stream()
            .anyMatch(
                p -> p.from().equals(offeringClan) && p.to().equals(own.id()) && p.expires() > now);
  }

  static Component buttons(
      String clanName, String acceptLabel, String declineLabel, boolean accept, boolean decline) {
    Component out = Component.empty();
    if (accept)
      out =
          out.append(
              Component.text(acceptLabel, DeveloperStyle.ACCEPT)
                  .clickEvent(ClickEvent.runCommand("/clan peace accept " + clanName)));
    if (accept && decline) out = out.append(Component.text("  ", DeveloperStyle.SYSTEM));
    if (decline)
      out =
          out.append(
              Component.text(declineLabel, DeveloperStyle.DECLINE)
                  .clickEvent(ClickEvent.runCommand("/clan peace deny " + clanName)));
    return out;
  }
}
