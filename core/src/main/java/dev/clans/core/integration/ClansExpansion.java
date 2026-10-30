package dev.clans.core.integration;

import dev.clans.api.ClansApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Loaded only when PlaceholderAPI is enabled. Stable public API; never shaded. */
public final class ClansExpansion extends PlaceholderExpansion implements AutoCloseable {
  private final ClansApi api;
  private final String version;
  private final LpcScope scopes;

  public ClansExpansion(ClansApi api, LpcScope scopes, String version) {
    this.version = version;
    this.api = api;
    this.scopes = scopes;
  }

  @Override
  public String getIdentifier() {
    return "clanscore";
  }

  @Override
  public String getAuthor() {
    return "ClansCore";
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public String onRequest(OfflinePlayer player, String parameters) {
    var field = ClanPlaceholders.Field.parse(parameters);
    if (field == null) return null;
    if (player == null) return "";
    var id = player.getUniqueId();
    String token = scopes.token(id, field);
    return token == null ? ClanPlaceholders.plain(api.snapshot(), id, field) : token;
  }

  @Override
  public void close() {
    unregister();
  }
}
