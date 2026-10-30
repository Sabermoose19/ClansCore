package dev.clans.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PermissionMetadataTest {
  @SuppressWarnings("unchecked")
  @Test
  void clanAliasUsesCanonicalRootPermissionAndWhoUsesInfoPermission() {
    Map<String, Object> plugin = new Yaml().load(getClass().getResourceAsStream("/plugin.yml"));
    var commands = (Map<String, Map<String, Object>>) plugin.get("commands");
    assertEquals(List.of("c"), commands.get("clan").get("aliases"));
    assertFalse(commands.containsKey("c"));
    String permission = (String) commands.get("clan").get("permission");
    Command alias = gate("c", permission);
    assertFalse(alias.testPermissionSilent(sender(Set.of())));
    assertTrue(alias.testPermissionSilent(sender(Set.of("clanscore.player.info"))));
    String canonical = ClansCore.canonicalSubcommand(false, "WHO");
    assertEquals("info", canonical);
    assertTrue(
        sender(Set.of("clanscore.player.info")).hasPermission("clanscore.player." + canonical));
    assertEquals("who", ClansCore.canonicalSubcommand(true, "who"));
    assertEquals("chat", ClansCore.canonicalSubcommand(false, "CHAT"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void rootVisibilityUsesActualBukkitPermissionChecksForEveryDelegatedCommand() {
    Map<String, Object> plugin = new Yaml().load(getClass().getResourceAsStream("/plugin.yml"));
    var commands = (Map<String, Map<String, Object>>) plugin.get("commands");
    Command admin = gate("clanadmin", (String) commands.get("clanadmin").get("permission"));
    assertFalse(admin.testPermissionSilent(sender(Set.of())));
    assertFalse(admin.testPermissionSilent(sender(Set.of("clanscore.player.help"))));
    for (String leaf : ClansCore.ADMIN.keySet())
      assertTrue(admin.testPermissionSilent(sender(Set.of("clanscore.admin." + leaf))), leaf);
    assertEquals(
        ClansCore.ADMIN.keySet().stream()
            .map(k -> "clanscore.admin." + k)
            .collect(java.util.stream.Collectors.toSet()),
        Set.of(admin.getPermission().split(";")));
    Command player = gate("clan", (String) commands.get("clan").get("permission"));
    assertEquals(
        Set.of(ClansCore.playerRootPermission(List.of()).split(";")),
        Set.of(player.getPermission().split(";")));
    assertFalse(player.testPermissionSilent(sender(Set.of())));
    assertFalse(player.testPermissionSilent(sender(Set.of("clanscore.admin.reload"))));
    for (String permission : player.getPermission().split(";"))
      assertTrue(player.testPermissionSilent(sender(Set.of(permission))), permission);
  }

  @Test
  void extensionPermissionMakesPlayerRootVisibleWithoutAnyCoreGrant() {
    Command with = gate("clan", ClansCore.playerRootPermission(List.of("example.extension")));
    Command without = gate("clan", ClansCore.playerRootPermission(List.of()));
    assertTrue(with.testPermissionSilent(sender(Set.of("example.extension"))));
    assertFalse(without.testPermissionSilent(sender(Set.of("example.extension"))));
    assertFalse(with.testPermissionSilent(sender(Set.of("clanschat.admin.reload"))));
  }

  static Command gate(String name, String permission) {
    assertNotNull(permission);
    Command command =
        new Command(name) {
          @Override
          public boolean execute(CommandSender sender, String label, String[] args) {
            return true;
          }
        };
    command.setPermission(permission);
    return command;
  }

  static CommandSender sender(Set<String> grants) {
    return (CommandSender)
        Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(),
            new Class<?>[] {CommandSender.class},
            (proxy, method, args) -> {
              if (method.getName().equals("hasPermission"))
                return grants.contains(
                    args[0] instanceof String s
                        ? s
                        : ((org.bukkit.permissions.Permission) args[0]).getName());
              if (method.getName().equals("getName")) return "Test";
              if (method.getReturnType() == boolean.class) return false;
              return null;
            });
  }

  @SuppressWarnings("unchecked")
  @Test
  void explicitWildcardsAndDefaults() {
    Map<String, Object> plugin = new Yaml().load(getClass().getResourceAsStream("/plugin.yml"));
    Map<String, Map<String, Object>> permissions =
        (Map<String, Map<String, Object>>) plugin.get("permissions");
    Map<String, Boolean> root =
        (Map<String, Boolean>) permissions.get("clanscore.*").get("children");
    assertEquals(Map.of("clanscore.player.*", true, "clanscore.admin.*", true), root);
    for (var group : Map.of("player", ClansCore.PLAYER, "admin", ClansCore.ADMIN).entrySet()) {
      Map<String, Boolean> children =
          (Map<String, Boolean>)
              permissions.get("clanscore." + group.getKey() + ".*").get("children");
      for (String command : group.getValue().keySet()) {
        String node = "clanscore." + group.getKey() + "." + command;
        assertEquals(true, children.get(node));
        assertEquals(
            group.getKey().equals("player") ? Boolean.TRUE : "op",
            permissions.get(node).get("default"));
      }
    }
    assertFalse(permissions.get("clanscore.admin.*").toString().contains("chat"));
  }
}
