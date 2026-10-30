package dev.clans.api;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;

/** Thread-safe immutable snapshots. No Bukkit or SQL access is performed by snapshot(). */
public interface ClansApi {
  enum Rank {
    LEADER,
    ELDER,
    MEMBER
  }

  record Member(UUID id, String name, Rank rank) {}

  record Clan(
      UUID id,
      String name,
      String motto,
      long founded,
      long renamed,
      boolean open,
      Map<UUID, Member> members,
      Set<UUID> enemies) {
    public Clan {
      members = Map.copyOf(members);
      enemies = Set.copyOf(enemies);
    }
  }

  record Invitation(UUID clan, UUID player, long expires) {}

  record Peace(UUID from, UUID to, long expires) {}

  record Snapshot(Map<UUID, Clan> clans, List<Invitation> invitations, List<Peace> peace) {
    public Snapshot {
      clans = Map.copyOf(clans);
      invitations = List.copyOf(invitations);
      peace = List.copyOf(peace);
    }

    public Clan clan(UUID player) {
      return clans.values().stream()
          .filter(c -> c.members.containsKey(player))
          .findFirst()
          .orElse(null);
    }

    public Clan named(String name) {
      return clans.values().stream()
          .filter(c -> c.name.equalsIgnoreCase(name))
          .findFirst()
          .orElse(null);
    }

    public UUID player(String name) {
      return clans.values().stream()
          .flatMap(c -> c.members.values().stream())
          .filter(m -> m.name.equalsIgnoreCase(name))
          .map(Member::id)
          .findFirst()
          .orElse(null);
    }
  }

  Snapshot snapshot();

  /** Listener runs on the database writer after commit; it must not call unsafe Bukkit APIs. */
  AutoCloseable subscribe(Consumer<Snapshot> listener);

  /** Register one uniquely named /clan subcommand. Close registration on plugin disable. */
  AutoCloseable registerExtension(String name, String permission, String usage, Extension handler);

  @FunctionalInterface
  interface Extension {
    void execute(CommandSender sender, String[] arguments);
  }
}
