package dev.clans.core.integration;

import dev.clans.api.ClansApi.Snapshot;
import io.papermc.paper.chat.ChatRenderer;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;

/** Decorates LPC's renderer; never supplies a separate chat layout or changes audiences. */
public final class LpcRenderer {
  private LpcRenderer() {}

  public static ChatRenderer wrap(
      ChatRenderer original, Snapshot snapshot, LpcScope.Context context) {
    return (source, displayName, message, viewer) -> {
      // Replace format tokens before putting the original message back. A player
      // typing %clanname% or a PAPI token must never have their message rewritten.
      Component result =
          original.render(source, displayName, Component.text(context.messageToken), viewer);
      UUID viewerId = viewer instanceof Player player ? player.getUniqueId() : null;
      for (var field : ClanPlaceholders.Field.values()) {
        Component replacement =
            ClanPlaceholders.component(snapshot, context.sender, viewerId, field);
        result = replace(result, context.token(field), replacement);
        result = replace(result, field.placeholder(), replacement);
      }
      result =
          replace(
              result,
              "%clanname%",
              ClanPlaceholders.component(
                  snapshot, context.sender, viewerId, ClanPlaceholders.Field.PREFIX));
      return replace(result, context.messageToken, message);
    };
  }

  private static Component replace(Component input, String token, Component value) {
    return input.replaceText(
        TextReplacementConfig.builder().matchLiteral(token).replacement(value).build());
  }
}
