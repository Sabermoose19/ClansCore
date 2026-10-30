package dev.clans.core;

import dev.clans.api.ClansApi.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** One writer, copy-on-write state, transactional persistence before publication. */
public final class Store implements AutoCloseable {
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "ClansCore-database"));
  private final AtomicReference<Snapshot> cache =
      new AtomicReference<>(new Snapshot(Map.of(), List.of(), List.of()));
  private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();
  private Connection db;

  public Store(Path path) {
    try {
      writer
          .submit(
              () -> {
                try {
                  Class.forName("org.sqlite.JDBC");
                  db = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
                  try (Statement s = db.createStatement()) {
                    s.execute("PRAGMA foreign_keys=ON");
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("PRAGMA busy_timeout=5000");
                    int version;
                    try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
                      version = r.getInt(1);
                    }
                    if (version > 1)
                      throw new SQLException("Database schema is newer than this plugin");
                    if (version == 0) {
                      db.setAutoCommit(false);
                      s.execute(
                          "CREATE TABLE clans(id TEXT PRIMARY KEY,name TEXT NOT NULL COLLATE NOCASE"
                              + " UNIQUE,motto TEXT NOT NULL,founded INTEGER NOT NULL,renamed"
                              + " INTEGER NOT NULL,open INTEGER NOT NULL,leader TEXT NOT"
                              + " NULL,leader_rank TEXT NOT NULL DEFAULT 'LEADER'"
                              + " CHECK(leader_rank='LEADER'),FOREIGN KEY(leader,id,leader_rank)"
                              + " REFERENCES members(uuid,clan,rank) DEFERRABLE INITIALLY"
                              + " DEFERRED)");
                      s.execute(
                          "CREATE TABLE members(uuid TEXT PRIMARY KEY,clan TEXT NOT NULL REFERENCES"
                              + " clans(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,name"
                              + " TEXT NOT NULL,rank TEXT NOT NULL CHECK(rank IN"
                              + " ('LEADER','ELDER','MEMBER')),UNIQUE(uuid,clan,rank))");
                      s.execute(
                          "CREATE UNIQUE INDEX one_leader ON members(clan) WHERE rank='LEADER'");
                      s.execute(
                          "CREATE TABLE enemies(a TEXT REFERENCES clans(id) ON DELETE CASCADE,b"
                              + " TEXT REFERENCES clans(id) ON DELETE CASCADE,PRIMARY"
                              + " KEY(a,b),CHECK(a<>b))");
                      s.execute(
                          "CREATE TABLE invitations(clan TEXT REFERENCES clans(id) ON DELETE"
                              + " CASCADE,player TEXT,expires INTEGER,PRIMARY KEY(clan,player))");
                      s.execute(
                          "CREATE TABLE peace(a TEXT REFERENCES clans(id) ON DELETE CASCADE,b TEXT"
                              + " REFERENCES clans(id) ON DELETE CASCADE,expires INTEGER,PRIMARY"
                              + " KEY(a,b),CHECK(a<>b))");
                      s.execute("PRAGMA user_version=1");
                      db.commit();
                      db.setAutoCommit(true);
                    }
                  }
                  cache.set(load());
                } catch (Exception e) {
                  throw new CompletionException(e);
                }
              })
          .get();
    } catch (Exception e) {
      writer.shutdownNow();
      throw new IllegalStateException("Cannot open clan database", e);
    }
  }

  public Snapshot snapshot() {
    return cache.get();
  }

  public AutoCloseable subscribe(Consumer<Snapshot> l) {
    listeners.add(l);
    return () -> listeners.remove(l);
  }

  public <T> CompletableFuture<T> mutate(Function<State, T> operation) {
    return CompletableFuture.supplyAsync(
        () -> {
          State state = new State(cache.get());
          T result = operation.apply(state);
          Snapshot next = state.freeze();
          validate(next);
          try {
            persist(next);
          } catch (SQLException e) {
            throw new CompletionException(e);
          }
          cache.set(next);
          for (var l : listeners) {
            try {
              l.accept(next);
            } catch (RuntimeException ignored) {
            }
          }
          return result;
        },
        writer);
  }

  static void validate(Snapshot s) {
    Set<UUID> members = new HashSet<>();
    Set<String> names = new HashSet<>();
    for (Clan c : s.clans().values()) {
      if (!names.add(c.name().toLowerCase(Locale.ROOT))
          || c.members().values().stream().filter(m -> m.rank() == Rank.LEADER).count() != 1)
        throw new IllegalStateException("Clan invariant failed");
      for (UUID id : c.members().keySet())
        if (!members.add(id)) throw new IllegalStateException("Duplicate membership");
      for (UUID enemy : c.enemies())
        if (enemy.equals(c.id())
            || !s.clans().containsKey(enemy)
            || !s.clans().get(enemy).enemies().contains(c.id()))
          throw new IllegalStateException("Relationship invariant failed");
    }
  }

  private void persist(Snapshot state) throws SQLException {
    db.setAutoCommit(false);
    try (Statement s = db.createStatement()) {
      s.execute("PRAGMA defer_foreign_keys=ON");
      for (String table : List.of("peace", "invitations", "enemies", "members", "clans"))
        s.executeUpdate("DELETE FROM " + table);
      for (Clan c : state.clans().values()) {
        insert(
            "INSERT INTO clans(id,name,motto,founded,renamed,open,leader) VALUES(?,?,?,?,?,?,?)",
            c.id(),
            c.name(),
            c.motto(),
            c.founded(),
            c.renamed(),
            c.open() ? 1 : 0,
            c.members().values().stream()
                .filter(m -> m.rank() == Rank.LEADER)
                .findFirst()
                .orElseThrow()
                .id());
        for (Member m : c.members().values())
          insert("INSERT INTO members VALUES(?,?,?,?)", m.id(), c.id(), m.name(), m.rank());
        for (UUID enemy : c.enemies()) insert("INSERT INTO enemies VALUES(?,?)", c.id(), enemy);
      }
      for (Invitation i : state.invitations())
        insert("INSERT INTO invitations VALUES(?,?,?)", i.clan(), i.player(), i.expires());
      for (Peace p : state.peace())
        insert("INSERT INTO peace VALUES(?,?,?)", p.from(), p.to(), p.expires());
      db.commit();
    } catch (SQLException e) {
      db.rollback();
      throw e;
    } finally {
      db.setAutoCommit(true);
    }
  }

  private void insert(String sql, Object... values) throws SQLException {
    try (PreparedStatement p = db.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) {
        Object v = values[i];
        p.setObject(i + 1, v instanceof UUID || v instanceof Rank ? v.toString() : v);
      }
      p.executeUpdate();
    }
  }

  private Snapshot load() throws SQLException {
    Map<UUID, Clan> clans = new HashMap<>();
    try (Statement st = db.createStatement();
        ResultSet r = st.executeQuery("SELECT * FROM clans")) {
      while (r.next()) {
        UUID id = UUID.fromString(r.getString("id"));
        clans.put(
            id,
            new Clan(
                id,
                r.getString("name"),
                r.getString("motto"),
                r.getLong("founded"),
                r.getLong("renamed"),
                r.getBoolean("open"),
                Map.of(),
                Set.of()));
      }
    }
    State state = new State(new Snapshot(clans, List.of(), List.of()));
    try (Statement st = db.createStatement();
        ResultSet r = st.executeQuery("SELECT * FROM members")) {
      while (r.next()) {
        UUID id = UUID.fromString(r.getString("clan"));
        var c = state.clans.get(id);
        Map<UUID, Member> ms = new HashMap<>(c.members());
        UUID player = UUID.fromString(r.getString("uuid"));
        ms.put(player, new Member(player, r.getString("name"), Rank.valueOf(r.getString("rank"))));
        state.members(c, ms);
      }
    }
    try (Statement st = db.createStatement();
        ResultSet r = st.executeQuery("SELECT * FROM enemies")) {
      while (r.next()) {
        Clan c = state.clans.get(UUID.fromString(r.getString("a")));
        Set<UUID> es = new HashSet<>(c.enemies());
        es.add(UUID.fromString(r.getString("b")));
        state.enemies(c, es);
      }
    }
    try (Statement st = db.createStatement();
        ResultSet r = st.executeQuery("SELECT * FROM invitations")) {
      while (r.next())
        state.invites.add(
            new Invitation(
                UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getLong(3)));
    }
    try (Statement st = db.createStatement();
        ResultSet r = st.executeQuery("SELECT * FROM peace")) {
      while (r.next())
        state.peace.add(
            new Peace(
                UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getLong(3)));
    }
    Snapshot result = state.freeze();
    validate(result);
    return result;
  }

  public void close() {
    writer.shutdown();
    try {
      if (!writer.awaitTermination(30, TimeUnit.SECONDS))
        throw new IllegalStateException("Database writer did not drain");
      if (db != null) db.close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static final class State {
    final Map<UUID, Clan> clans;
    final List<Invitation> invites;
    final List<Peace> peace;

    State(Snapshot s) {
      clans = new HashMap<>(s.clans());
      invites = new ArrayList<>(s.invitations());
      peace = new ArrayList<>(s.peace());
    }

    Snapshot freeze() {
      return new Snapshot(clans, invites, peace);
    }

    void members(Clan c, Map<UUID, Member> m) {
      clans.put(
          c.id(),
          new Clan(
              c.id(), c.name(), c.motto(), c.founded(), c.renamed(), c.open(), m, c.enemies()));
    }

    void enemies(Clan c, Set<UUID> e) {
      clans.put(
          c.id(),
          new Clan(
              c.id(), c.name(), c.motto(), c.founded(), c.renamed(), c.open(), c.members(), e));
    }
  }
}
