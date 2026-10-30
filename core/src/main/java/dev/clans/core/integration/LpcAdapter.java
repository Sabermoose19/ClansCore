package dev.clans.core.integration;

import dev.clans.api.ClansApi;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.*;

/** Core soft-depends on LPC, so these HIGHEST handlers register after LPC's. */
public final class LpcAdapter implements Listener {
  private final ClansApi api;
  private final LpcScope scopes;

  public LpcAdapter(ClansApi api, LpcScope scopes) {
    this.api = api;
    this.scopes = scopes;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void begin(AsyncChatEvent event) {
    scopes.begin(event, event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void finish(AsyncChatEvent event) {
    var context = scopes.end(event);
    if (context != null && !event.isCancelled())
      event.renderer(LpcRenderer.wrap(event.renderer(), api.snapshot(), context));
  }
}
