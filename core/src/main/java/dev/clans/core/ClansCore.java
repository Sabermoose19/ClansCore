package dev.clans.core;

import dev.clans.api.ClanNameStyle;
import dev.clans.api.ClansApi;
import dev.clans.api.ClansApi.*;
import dev.clans.api.DeveloperStyle;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClansCore extends JavaPlugin
    implements ClansApi, Listener, CommandExecutor, TabCompleter {
  Store store;
  ClanService service;
  private final Map<UUID, Player> online = new ConcurrentHashMap<>();
  private final Map<String, UUID> names = new ConcurrentHashMap<>();
  private final Map<String, Extra> extensions = new ConcurrentHashMap<>();
  private volatile Map<String, String> messages = Map.of();
  private volatile boolean notify = true;
  private volatile List<Translation> translations = List.of();

  record Translation(java.util.regex.Pattern pattern, String replacement) {}

  record Extra(String permission, String usage, Extension handler) {}

  static final Map<String, String> PLAYER =
      Map.ofEntries(
          Map.entry("help", "[page]"),
          Map.entry("create", "<name>"),
          Map.entry("info", "[clan] [page]"),
          Map.entry("members", "[clan] [page]"),
          Map.entry("list", "[page]"),
          Map.entry("invites", ""),
          Map.entry("invite", "<player>"),
          Map.entry("uninvite", "<player>"),
          Map.entry("accept", "<clan>"),
          Map.entry("deny", "<clan>"),
          Map.entry("join", "<clan>"),
          Map.entry("leave", ""),
          Map.entry("kick", "<player>"),
          Map.entry("promote", "<player>"),
          Map.entry("demote", "<player>"),
          Map.entry("transfer", "<player>"),
          Map.entry("rename", "<name>"),
          Map.entry("motto", "<text...|clear>"),
          Map.entry("open", ""),
          Map.entry("close", ""),
          Map.entry("enemy", "<clan>"),
          Map.entry("peace", "<clan>|accept <clan>|deny <clan>"),
          Map.entry("disband", ""),
          Map.entry("confirm", ""),
          Map.entry("cancel", ""));
  static final Map<String, String> ADMIN =
      Map.ofEntries(
          Map.entry("help", ""),
          Map.entry("info", "<clan> [page]"),
          Map.entry("rename", "<clan> <name>"),
          Map.entry("motto", "<clan> <text...>"),
          Map.entry("clearmotto", "<clan>"),
          Map.entry("remove", "<player>"),
          Map.entry("setrank", "<player> <member|elder>"),
          Map.entry("setleader", "<clan> <player>"),
          Map.entry("open", "<clan>"),
          Map.entry("close", "<clan>"),
          Map.entry("neutral", "<clanA> <clanB>"),
          Map.entry("disband", "<clan>"),
          Map.entry("confirm", ""),
          Map.entry("cancel", ""),
          Map.entry("reload", ""));

  @Override
  public void onEnable() {
    saveDefaultConfig();
    try {
      Settings cfg = readSettings();
      readMessages();
      store = new Store(getDataFolder().toPath().resolve("clans.db"));
      service = new ClanService(store, cfg, Clock.systemUTC());
      getServer().getServicesManager().register(ClansApi.class, this, this, ServicePriority.Normal);
      getServer().getPluginManager().registerEvents(this, this);
      for (String command : List.of("clan", "clanadmin")) {
        getCommand(command).setExecutor(this);
        getCommand(command).setTabCompleter(this);
      }
      for (Player p : getServer().getOnlinePlayers()) track(p);
    } catch (Exception e) {
      getLogger().log(java.util.logging.Level.SEVERE, "ClansCore startup failed", e);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  Settings readSettings() {
    return new Settings(
        getConfig().getInt("names.min", 3),
        getConfig().getInt("names.max", 16),
        getConfig().getInt("motto-max", 100),
        getConfig().getInt("member-limit", 0),
        getConfig().getLong("invite-seconds", 600) * 1000,
        getConfig().getLong("peace-seconds", 86400) * 1000,
        getConfig().getLong("rename-cooldown-seconds", 86400) * 1000,
        new HashSet<>(getConfig().getStringList("names.reserved")),
        getConfig().getString("date.timezone", "UTC"),
        getConfig().getString("date.format", "yyyy-MM-dd HH:mm z"));
  }

  void readMessages() {
    List<Translation> rules = new ArrayList<>();
    for (var entry : DeveloperStyle.RESPONSE_REPLACEMENTS) {
      rules.add(new Translation(java.util.regex.Pattern.compile(entry.getKey()), entry.getValue()));
    }
    messages = DeveloperStyle.CORE_MESSAGES;
    translations = List.copyOf(rules);
    notify = getConfig().getBoolean("notifications", true);
    peaceButtons = getConfig().getBoolean("peace-buttons", true);
  }

  @Override
  public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
    extensions.clear();
    if (store != null) store.close();
    online.clear();
  }

  void track(Player p) {
    online.put(p.getUniqueId(), p);
    names.entrySet().removeIf(e -> e.getValue().equals(p.getUniqueId()));
    names.put(p.getName().toLowerCase(Locale.ROOT), p.getUniqueId());
    service
        .remember(p.getUniqueId(), p.getName())
        .exceptionally(
            e -> {
              getLogger().warning("Could not persist username: " + e.getMessage());
              return null;
            });
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    track(e.getPlayer());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    online.remove(e.getPlayer().getUniqueId());
  }

  public Snapshot snapshot() {
    return store.snapshot();
  }

  public AutoCloseable subscribe(Consumer<Snapshot> listener) {
    return store.subscribe(listener);
  }

  public AutoCloseable registerExtension(
      String name, String permission, String usage, Extension handler) {
    if (!name.matches("[a-z]+")
        || PLAYER.containsKey(canonicalSubcommand(false, name))
        || extensions.putIfAbsent(name, new Extra(permission, usage, handler)) != null)
      throw new IllegalArgumentException("Duplicate/invalid extension");
    Permission parent = getServer().getPluginManager().getPermission("clanscore.player.*");
    parent.getChildren().put(permission, true);
    parent.recalculatePermissibles();
    refreshPlayerRoot();
    return () -> {
      extensions.remove(name);
      parent.getChildren().remove(permission);
      parent.recalculatePermissibles();
      refreshPlayerRoot();
    };
  }

  void refreshPlayerRoot() {
    getCommand("clan")
        .setPermission(
            playerRootPermission(extensions.values().stream().map(Extra::permission).toList()));
    for (Player player : online.values())
      player.getScheduler().run(this, task -> player.updateCommands(), null);
  }

  static String playerRootPermission(Collection<String> extensionPermissions) {
    Set<String> permissions = new TreeSet<>();
    PLAYER.keySet().forEach(k -> permissions.add("clanscore.player." + k));
    permissions.add("clanscore.player.peace.accept");
    permissions.add("clanscore.player.peace.deny");
    permissions.addAll(extensionPermissions);
    return String.join(";", permissions);
  }

  void reply(CommandSender sender, String key, String text) {
    for (Translation rule : translations)
      text = rule.pattern.matcher(text).replaceAll(rule.replacement);
    String template = messages.getOrDefault(key, "{text}");
    Component result = MessageFormat.wrap(template, Component.text(text));
    Runnable send = () -> sender.sendMessage(result);
    if (sender instanceof Player p) p.getScheduler().run(this, t -> send.run(), null);
    else getServer().getGlobalRegionScheduler().execute(this, send);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] input) {
    boolean admin = command.getName().equals("clanadmin");
    String root = admin ? "clanadmin" : "clan";
    String sub = canonicalSubcommand(admin, input.length == 0 ? "help" : input[0]);
    String[] args = Arrays.copyOfRange(input, Math.min(1, input.length), input.length);
    try {
      if (!admin && extensions.containsKey(sub)) {
        Extra e = extensions.get(sub);
        check(sender, e.permission);
        e.handler.execute(sender, args);
        return true;
      }
      Map<String, String> commands = admin ? ADMIN : PLAYER;
      ClanService.require(commands.containsKey(sub), "Unknown command. Use /" + root + " help.");
      String permission = "clanscore." + (admin ? "admin." : "player.") + sub;
      if (sub.equals("peace")
          && args.length > 0
          && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))
          && !admin) permission += "." + args[0].toLowerCase(Locale.ROOT);
      check(sender, permission);
      if (sub.equals("help")) {
        help(sender, admin, args);
        return true;
      }
      if (sub.equals("reload")) {
        Settings old = service.settings;
        reloadConfig();
        try {
          Settings next = readSettings();
          service.settings = next;
          readMessages();
          reply(sender, "success", DeveloperStyle.CORE_RELOADED);
          getLogger().info("STAFF " + sender.getName() + " reload configuration");
        } catch (RuntimeException e) {
          service.settings = old;
          throw e;
        }
        return true;
      }
      if (Set.of("info", "members", "list", "invites").contains(sub)) {
        read(sender, admin, sub, args);
        return true;
      }
      ClanService.require(admin || sender instanceof Player, "This command requires a player.");
      int minimum =
          switch (sub) {
            case "create",
                "invite",
                "uninvite",
                "accept",
                "deny",
                "join",
                "kick",
                "promote",
                "demote",
                "transfer",
                "rename",
                "motto",
                "enemy",
                "peace",
                "neutral",
                "remove",
                "setrank",
                "setleader",
                "clearmotto" ->
                1;
            default -> 0;
          };
      if (admin && Set.of("rename", "motto", "setrank", "setleader", "neutral").contains(sub))
        minimum = 2;
      if (admin && Set.of("open", "close", "disband").contains(sub)) minimum = 1;
      ClanService.require(
          args.length >= minimum, "Usage: /" + root + " " + sub + " " + commands.get(sub));
      int maximum = !admin && sub.equals("peace") ? 2 : minimum;
      ClanService.require(
          sub.equals("motto") || args.length <= maximum,
          "Usage: /" + root + " " + sub + " " + commands.get(sub));
      String action = sub;
      if (!admin && sub.equals("peace") && args.length >= 2) {
        action =
            switch (args[0].toLowerCase(Locale.ROOT)) {
              case "accept" -> "peaceaccept";
              case "deny" -> "peacedeny";
              default ->
                  throw new IllegalArgumentException(
                      "Use /clan peace <clan> or peace accept|deny <clan>.");
            };
        args = new String[] {args[1]};
      }
      if (sub.equals("setrank"))
        ClanService.require(
            Set.of("member", "elder").contains(args[1].toLowerCase(Locale.ROOT)),
            "Rank must be member or elder.");
      UUID target = null;
      String targetName = null;
      if (Set.of(
              "invite",
              "uninvite",
              "kick",
              "promote",
              "demote",
              "transfer",
              "remove",
              "setrank",
              "setleader")
          .contains(sub)) {
        targetName = args[sub.equals("setleader") ? 1 : 0];
        target = resolve(targetName);
        ClanService.require(
            target != null,
            "Unknown player. Invitations require a player seen by this installation; ask them to"
                + " log in first.");
      }
      UUID actor =
          sender instanceof Player p
              ? p.getUniqueId()
              : UUID.nameUUIDFromBytes(
                  ("console:" + sender.getName())
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      if (sub.equals("confirm")) {
        String pending =
            service.pendingAction(new ClanService.Actor(actor, sender.getName(), admin));
        if (pending != null) check(sender, "clanscore." + (admin ? "admin." : "player.") + pending);
      }
      String[] finalArgs = args;
      String finalAction = action;
      Snapshot before = snapshot();
      service
          .executeDetailed(
              new ClanService.Actor(actor, sender.getName(), admin),
              action,
              args,
              target,
              targetName)
          .whenComplete(
              (result, error) -> {
                if (error != null) {
                  Throwable cause = error;
                  while (cause.getCause() != null) cause = cause.getCause();
                  if (cause instanceof IllegalArgumentException)
                    reply(sender, "error", cause.getMessage());
                  else {
                    getLogger().log(java.util.logging.Level.SEVERE, "Clan operation failed", cause);
                    reply(
                        sender,
                        "error",
                        "Database operation failed; no changes published. Contact an"
                            + " administrator.");
                  }
                } else {
                  if (!result.message().isEmpty()) reply(sender, "success", result.message());
                  if (admin)
                    getLogger()
                        .info(
                            "STAFF "
                                + sender.getName()
                                + " "
                                + finalAction
                                + " "
                                + String.join(" ", finalArgs)
                                + " -> "
                                + result.message());
                  if (result.notices().isEmpty()) notifyChanges(sender, actor, before, snapshot());
                  else
                    for (ClanService.Notice notice : result.notices()) {
                      for (UUID id : notice.recipients()) {
                        Player recipient = online.get(id);
                        if (recipient != null && (notify || id.equals(actor)))
                          relationshipNotice(recipient, notice);
                      }
                    }
                }
              });
    } catch (IllegalArgumentException e) {
      reply(sender, "error", e.getMessage());
    }
    return true;
  }

  static void check(CommandSender s, String p) {
    ClanService.require(s.hasPermission(p), "Missing permission: " + p);
  }

  static String canonicalSubcommand(boolean admin, String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    return !admin && normalized.equals("who") ? "info" : normalized;
  }

  void relationshipNotice(Player recipient, ClanService.Notice notice) {
    recipient
        .getScheduler()
        .run(
            this,
            task -> {
              Snapshot current = snapshot();
              UUID viewer = recipient.getUniqueId();
              Clan own = current.clan(viewer);
              Clan other = current.clans().get(notice.otherClanId());
              ClanNameStyle style = getServer().getServicesManager().load(ClanNameStyle.class);
              if (style == null)
                style = (id, player) -> MessageFormat.clanColour(current, id, player);
              Component formatted =
                  MessageFormat.relationship(
                      messages.getOrDefault(notice.key(), notice.defaultText()),
                      other == null ? notice.otherClan() : other.name(),
                      style.colour(notice.otherClanId(), viewer),
                      own == null ? "" : own.name(),
                      style.colour(own == null ? null : own.id(), viewer));
              Component line =
                  MessageFormat.wrap(messages.getOrDefault("notification", "{text}"), formatted);
              recipient.sendMessage(line);
              if (!notice.key().equals("peace-received") || !peaceButtons) return;
              if (other == null
                  || !MessageFormat.mayRespond(
                      current, recipient.getUniqueId(), other.id(), System.currentTimeMillis()))
                return;
              boolean accept = recipient.hasPermission("clanscore.player.peace.accept");
              boolean deny = recipient.hasPermission("clanscore.player.peace.deny");
              if (accept || deny)
                recipient.sendMessage(
                    MessageFormat.buttons(
                        other.name(),
                        messages.getOrDefault("peace-accept-button", "[Accept peace]"),
                        messages.getOrDefault("peace-decline-button", "[Decline]"),
                        accept,
                        deny));
            },
            null);
  }

  private volatile boolean peaceButtons = true;

  UUID resolve(String name) {
    UUID id = names.get(name.toLowerCase(Locale.ROOT));
    if (id != null) return id;
    id = snapshot().player(name);
    if (id != null) return id;
    var p = getServer().getOfflinePlayerIfCached(name);
    return p == null ? null : p.getUniqueId();
  }

  void help(CommandSender sender, boolean admin, String[] args) {
    Map<String, String> commands = new TreeMap<>(admin ? ADMIN : PLAYER);
    if (!admin) {
      commands.put("who", PLAYER.get("info"));
      commands.put("peace", "<clan>");
      commands.put("peace accept", "<clan>");
      commands.put("peace deny", "<clan>");
    }
    if (!admin) extensions.forEach((k, v) -> commands.put(k, v.usage));
    List<Component> lines = new ArrayList<>();
    commands.forEach(
        (k, v) -> {
          String p =
              extensions.containsKey(k) && !admin
                  ? extensions.get(k).permission
                  : "clanscore."
                      + (admin ? "admin." : "player.")
                      + canonicalSubcommand(admin, k).replace(' ', '.');
          if (sender.hasPermission(p) && (admin || available(sender, k)))
            lines.add(
                DeveloperStyle.render(
                    DeveloperStyle.HELP_ROW,
                    Map.of(
                        "command", text("/" + (admin ? "clanadmin" : "clan") + " " + k),
                        "usage", text(v))));
        });
    formattedPage(
        sender,
        lines,
        pageNumber(args, 0),
        DeveloperStyle.HELP_PAGE_SIZE,
        DeveloperStyle.HELP_HEADER,
        DeveloperStyle.HELP_FOOTER,
        Map.of(
            "root", text(admin ? "clanadmin" : "clan"),
            "title",
                Component.text(
                    admin ? DeveloperStyle.ADMIN_HELP_TITLE : DeveloperStyle.HELP_TITLE)));
  }

  boolean available(CommandSender sender, String command) {
    command = canonicalSubcommand(false, command.split(" ", 2)[0]);
    if (Set.of("help", "info", "members", "list").contains(command)) return true;
    if (!(sender instanceof Player p)) return false;
    Clan c = snapshot().clan(p.getUniqueId());
    if (Set.of("create", "accept", "deny", "join").contains(command)) return c == null;
    if (Set.of("invites", "cancel", "confirm").contains(command)) return true;
    if (c == null) return false;
    Rank rank = c.members().get(p.getUniqueId()).rank();
    if (command.equals("leave")) return rank != Rank.LEADER;
    if (Set.of("invite", "uninvite", "kick", "motto").contains(command)) return rank != Rank.MEMBER;
    if (Set.of(
            "promote", "demote", "transfer", "rename", "open", "close", "enemy", "peace", "disband")
        .contains(command)) return rank == Rank.LEADER;
    return true;
  }

  int pageNumber(String[] args, int index) {
    if (args.length <= index) return 1;
    try {
      int n = Integer.parseInt(args[index]);
      ClanService.require(n > 0, "Page must be positive.");
      return n;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid page.");
    }
  }

  static Component text(Object value) {
    return DeveloperStyle.value(value);
  }

  void styled(CommandSender sender, String template, Map<String, Component> values) {
    sendStyled(sender, DeveloperStyle.render(template, values));
  }

  void sendStyled(CommandSender sender, Component content) {
    Component out = MessageFormat.wrap(messages.getOrDefault("line", "{text}"), content);
    Runnable send = () -> sender.sendMessage(out);
    if (sender instanceof Player p) p.getScheduler().run(this, task -> send.run(), null);
    else getServer().getGlobalRegionScheduler().execute(this, send);
  }

  void formattedPage(
      CommandSender sender,
      List<Component> lines,
      int page,
      int size,
      String header,
      String footer,
      Map<String, Component> context) {
    DeveloperStyle.Page<Component> selected = DeveloperStyle.page(lines, page, size);
    Map<String, Component> values = new HashMap<>(context);
    values.put("page", text(selected.number()));
    values.put("pages", text(selected.pages()));
    if (!header.isEmpty()) styled(sender, header, values);
    selected.rows().forEach(row -> sendStyled(sender, row));
    if (!footer.isEmpty()) styled(sender, footer, values);
  }

  Component clanName(CommandSender sender, Snapshot snapshot, Clan clan) {
    if (!(sender instanceof Player p))
      return Component.text(clan.name(), DeveloperStyle.NEUTRAL_CLAN);
    ClanNameStyle style = getServer().getServicesManager().load(ClanNameStyle.class);
    return Component.text(
        clan.name(),
        style == null
            ? MessageFormat.clanColour(snapshot, clan.id(), p.getUniqueId())
            : style.colour(clan.id(), p.getUniqueId()));
  }

  void read(CommandSender sender, boolean admin, String sub, String[] args) {
    Snapshot s = snapshot();
    if (sub.equals("list")) {
      List<Component> rows =
          s.clans().values().stream()
              .sorted(Comparator.comparing(Clan::name, String.CASE_INSENSITIVE_ORDER))
              .map(
                  c ->
                      DeveloperStyle.render(
                          DeveloperStyle.LIST_ROW,
                          Map.of(
                              "clan",
                              clanName(sender, s, c),
                              "total",
                              text(c.members().size()),
                              "policy",
                              text(c.open() ? DeveloperStyle.OPEN : DeveloperStyle.INVITE_ONLY))))
              .toList();
      formattedPage(
          sender,
          rows,
          pageNumber(args, 0),
          DeveloperStyle.LIST_PAGE_SIZE,
          DeveloperStyle.LIST_HEADER,
          DeveloperStyle.LIST_FOOTER,
          Map.of());
      return;
    }
    if (sub.equals("invites")) {
      ClanService.require(sender instanceof Player, "Requires a player.");
      UUID id = ((Player) sender).getUniqueId();
      long now = System.currentTimeMillis();
      List<Invitation> invitations =
          s.invitations().stream()
              .filter(i -> i.player().equals(id) && i.expires() > now)
              .sorted(Comparator.comparingLong(Invitation::expires))
              .toList();
      styled(sender, DeveloperStyle.INVITES_HEADER, Map.of());
      if (invitations.isEmpty())
        sendStyled(sender, Component.text(DeveloperStyle.NO_INVITES, DeveloperStyle.SYSTEM));
      else {
        for (Invitation i : invitations)
          styled(
              sender,
              DeveloperStyle.INVITE_ROW,
              Map.of(
                  "clan",
                  clanName(sender, s, s.clans().get(i.clan())),
                  "expires",
                  text(date(i.expires()))));
        styled(sender, DeveloperStyle.INVITES_FOOTER, Map.of());
      }
      return;
    }
    Clan c;
    int page;
    if (args.length == 0 || (!admin && args[0].matches("[0-9]+") && s.named(args[0]) == null)) {
      ClanService.require(sender instanceof Player, "Specify a clan.");
      c = s.clan(((Player) sender).getUniqueId());
      page = pageNumber(args, 0);
    } else {
      c = ClanService.named(s, args[0]);
      page = pageNumber(args, 1);
    }
    ClanService.require(c != null, "You have no clan.");
    // Validate before emitting headers; retain all members on subsequent pages.
    List<Member> ordered =
        c.members().values().stream()
            .sorted(
                Comparator.comparing(Member::rank)
                    .thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    DeveloperStyle.page(ordered, page, DeveloperStyle.MEMBER_PAGE_SIZE);
    Map<String, Component> values = new HashMap<>();
    values.put("page", text(page));
    values.put(
        "pages", text(DeveloperStyle.page(ordered, page, DeveloperStyle.MEMBER_PAGE_SIZE).pages()));
    values.put("clan", clanName(sender, s, c));
    values.put("id", text(c.id()));
    values.put("motto", text(c.motto().isEmpty() ? DeveloperStyle.NO_MOTTO : c.motto()));
    values.put("founded", text(date(c.founded())));
    values.put("policy", text(c.open() ? DeveloperStyle.OPEN : DeveloperStyle.INVITE_ONLY));
    values.put("total", text(c.members().size()));
    values.put("online", text(c.members().keySet().stream().filter(online::containsKey).count()));
    values.put(
        "offline",
        text(c.members().keySet().stream().filter(id -> !online.containsKey(id)).count()));
    values.put("enemy-count", text(c.enemies().size()));
    Member leader = ordered.stream().filter(m -> m.rank() == Rank.LEADER).findFirst().orElseThrow();
    values.put("leader", Component.text(leader.name(), DeveloperStyle.PLAYER_NAME));
    List<Component> enemies =
        c.enemies().stream()
            .map(s.clans()::get)
            .sorted(Comparator.comparing(Clan::name, String.CASE_INSENSITIVE_ORDER))
            .map(enemy -> clanName(sender, s, enemy))
            .toList();
    values.put(
        "enemies",
        enemies.isEmpty()
            ? text(DeveloperStyle.NONE)
            : Component.join(
                net.kyori.adventure.text.JoinConfiguration.separator(
                    DeveloperStyle.render(DeveloperStyle.CLAN_SEPARATOR, Map.of())),
                enemies));
    values.put("command", text("/" + (admin ? "clanadmin info" : "clan members") + " " + c.name()));
    if (sub.equals("info")) {
      for (String template : DeveloperStyle.INFO_LINES) styled(sender, template, values);
      if (admin)
        for (String template : DeveloperStyle.ADMIN_INFO_EXTRA) styled(sender, template, values);
      if (sender instanceof Player p
          && c.members().containsKey(p.getUniqueId())
          && c.members().get(p.getUniqueId()).rank() == Rank.LEADER) {
        List<Peace> peace =
            s.peace().stream()
                .filter(r -> r.to().equals(c.id()) && r.expires() > System.currentTimeMillis())
                .toList();
        sendStyled(sender, DeveloperStyle.peaceHeader(peace.isEmpty()));
        if (!peace.isEmpty())
          for (Peace offer : peace)
            styled(
                sender,
                DeveloperStyle.PEACE_ROW,
                Map.of(
                    "clan",
                    clanName(sender, s, s.clans().get(offer.from())),
                    "expires",
                    text(date(offer.expires()))));
      }
    }
    styled(sender, DeveloperStyle.MEMBERS_HEADER, values);
    for (var section :
        DeveloperStyle.memberSections(ordered, page, DeveloperStyle.MEMBER_PAGE_SIZE)) {
      styled(
          sender,
          DeveloperStyle.MEMBER_GROUP_HEADER,
          Map.of(
              "rank", Component.text(DeveloperStyle.RANK_SECTION_NAMES.get(section.rank())),
              "count", text(section.total()),
              "empty", section.rows().isEmpty() ? text(DeveloperStyle.NONE) : Component.empty()));
      for (Member member : section.rows())
        styled(
            sender,
            DeveloperStyle.MEMBER_ROW,
            Map.of(
                "rank", Component.text(DeveloperStyle.RANK_NAMES.get(member.rank())),
                "username", Component.text(member.name(), DeveloperStyle.PLAYER_NAME),
                "status",
                    DeveloperStyle.render(
                        online.containsKey(member.id())
                            ? DeveloperStyle.ONLINE
                            : DeveloperStyle.OFFLINE,
                        Map.of())));
    }
    styled(sender, DeveloperStyle.MEMBERS_FOOTER, values);
  }

  String date(long time) {
    Settings cfg = service.settings;
    return DateTimeFormatter.ofPattern(cfg.dateFormat())
        .withZone(ZoneId.of(cfg.timezone()))
        .format(Instant.ofEpochMilli(time));
  }

  void notifyChanges(CommandSender sender, UUID actor, Snapshot old, Snapshot next) {
    if (!notify) return;
    Set<UUID> affected = new HashSet<>();
    for (Clan c : old.clans().values())
      if (!c.equals(next.clans().get(c.id()))) affected.addAll(c.members().keySet());
    for (Clan c : next.clans().values())
      if (!c.equals(old.clans().get(c.id()))) affected.addAll(c.members().keySet());
    for (Invitation i : next.invitations())
      if (!old.invitations().contains(i)) affected.add(i.player());
    for (Peace p : next.peace())
      if (!old.peace().contains(p)) {
        Clan c = next.clans().get(p.to());
        if (c != null)
          c.members().values().stream()
              .filter(m -> m.rank() == Rank.LEADER)
              .forEach(m -> affected.add(m.id()));
      }
    for (UUID id : affected) {
      Player p = online.get(id);
      if (p != null && !id.equals(actor))
        reply(
            p,
            "notification",
            "Clan information changed by "
                + sender.getName()
                + ". Use /clan info or /clan invites for details.");
    }
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    boolean admin = command.getName().equals("clanadmin");
    if (args.length == 1) {
      Set<String> keys = new TreeSet<>((admin ? ADMIN : PLAYER).keySet());
      if (!admin) {
        keys.addAll(extensions.keySet());
        keys.add("who");
      }
      return keys.stream()
          .filter(k -> k.startsWith(args[0].toLowerCase(Locale.ROOT)))
          .filter(
              k ->
                  (k.equals("peace") && !admin
                      ? sender.hasPermission("clanscore.player.peace")
                          || sender.hasPermission("clanscore.player.peace.accept")
                          || sender.hasPermission("clanscore.player.peace.deny")
                      : sender.hasPermission(
                          !admin && extensions.containsKey(k)
                              ? extensions.get(k).permission
                              : "clanscore."
                                  + (admin ? "admin." : "player.")
                                  + canonicalSubcommand(admin, k))))
          .filter(k -> admin || available(sender, k))
          .toList();
    }
    if (!admin
        && args.length == 2
        && args[0].equalsIgnoreCase("peace")
        && available(sender, "peace"))
      return List.of("accept", "deny").stream()
          .filter(k -> k.startsWith(args[1].toLowerCase(Locale.ROOT)))
          .filter(k -> sender.hasPermission("clanscore.player.peace." + k))
          .toList();
    return List.of();
  }
}
