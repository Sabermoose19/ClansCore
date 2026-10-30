package dev.clans.core.integration;

import dev.clans.api.ClansApi;
import java.util.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/** No hard linkage to PAPI in Core's entry point, allowing dependency-free Core startup. */
public final class LpcSupport {
  private LpcSupport() {}

  public static AutoCloseable start(JavaPlugin owner, ClansApi api) {
    List<AutoCloseable> handles = new ArrayList<>();
    LpcScope scopes = new LpcScope();
    var plugins = owner.getServer().getPluginManager();
    if (owner.getConfig().getBoolean("integrations.placeholderapi", true)
        && plugins.isPluginEnabled("PlaceholderAPI")) {
      try {
        ClansExpansion expansion =
            new ClansExpansion(api, scopes, owner.getPluginMeta().getVersion());
        if (expansion.register()) {
          handles.add(expansion);
          owner
              .getLogger()
              .info(
                  "PlaceholderAPI: registered %clanscore_name%, %clanscore_stars%,"
                      + " %clanscore_chatprefix%");
        } else
          owner
              .getLogger()
              .warning(
                  "PlaceholderAPI identifier clanscore is already registered; LPC's %clanname%"
                      + " token remains available.");
      } catch (RuntimeException | LinkageError failure) {
        owner
            .getLogger()
            .warning("PlaceholderAPI integration unavailable: " + failure.getMessage());
      }
    }
    if (owner.getConfig().getBoolean("integrations.lpc", true) && plugins.isPluginEnabled("LPC")) {
      try {
        LpcAdapter adapter = new LpcAdapter(api, scopes);
        plugins.registerEvents(adapter, owner);
        handles.add(() -> HandlerList.unregisterAll(adapter));
        owner
            .getLogger()
            .info(
                "LPC: viewer-aware clan placeholders enabled; use %clanname% in LPC's"
                    + " chat-format.");
      } catch (RuntimeException | LinkageError failure) {
        owner.getLogger().warning("LPC integration unavailable: " + failure.getMessage());
      }
    }
    return () -> {
      Collections.reverse(handles);
      for (var handle : handles) handle.close();
    };
  }
}
