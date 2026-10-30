package dev.clans.api;

import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;

/** Optional thread-safe viewer-relative clan-name colours supplied by a chat extension. */
@FunctionalInterface
public interface ClanNameStyle {
  TextColor colour(UUID clanId, UUID viewerId);
}
