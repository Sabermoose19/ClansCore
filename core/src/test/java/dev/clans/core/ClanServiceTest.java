package dev.clans.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.clans.api.ClansApi.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ClanServiceTest {
  @Test
  void relationshipMessagesUseCorrectPerspectiveAndClanRecipients() {
    ok(a, "create", "Alpha");
    ok(b, "create", "Bravo");
    member(a, c, "Alpha");
    var war = svc.executeDetailed(a, "enemy", new String[] {"Bravo"}, null, null).join();
    assertEquals("", war.message());
    assertEquals(Set.of(a.id(), c.id()), war.notices().get(0).recipients());
    assertEquals(Set.of(b.id()), war.notices().get(1).recipients());
    assertEquals("You have declared war on Bravo!", render(war.notices().get(0)));
    assertEquals("Alpha has declared war on your clan!", render(war.notices().get(1)));
    var offer = svc.executeDetailed(a, "peace", new String[] {"Bravo"}, null, null).join();
    assertEquals("You sent Bravo a peace offer.", render(offer.notices().get(0)));
    assertEquals("Alpha has offered peace with your clan.", render(offer.notices().get(1)));
    var accepted = svc.executeDetailed(b, "peaceaccept", new String[] {"Alpha"}, null, null).join();
    assertEquals(Set.of(b.id()), accepted.notices().get(0).recipients());
    assertEquals(Set.of(a.id(), c.id()), accepted.notices().get(1).recipients());
    assertEquals("You have made peace with Alpha.", render(accepted.notices().get(0)));
    assertEquals("Your peace treaty with Bravo was accepted.", render(accepted.notices().get(1)));
    assertTrue(store.snapshot().clan(a.id()).enemies().isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> war.notices().get(0).recipients().clear());
  }

  String render(ClanService.Notice notice) {
    return notice.defaultText().replace("{otherclan}", notice.otherClan());
  }

  @Test
  void failedRelationshipCommandsDoNotProduceSuccessNotices() {
    ok(a, "create", "Alpha");
    ok(b, "create", "Bravo");
    assertThrows(
        CompletionException.class,
        () -> svc.executeDetailed(a, "peace", new String[] {"Bravo"}, null, null).join());
    ok(a, "enemy", "Bravo");
    Snapshot before = store.snapshot();
    assertThrows(
        CompletionException.class,
        () -> svc.executeDetailed(a, "enemy", new String[] {"Bravo"}, null, null).join());
    assertSame(before, store.snapshot());
    assertThrows(
        CompletionException.class,
        () -> svc.executeDetailed(b, "peaceaccept", new String[] {"Alpha"}, null, null).join());
  }

  @Test
  void foundingAlsoClearsObsoleteInvitations() {
    ok(a, "create", "Alpha");
    run(a, "invite", b.id(), "Bob").join();
    ok(b, "create", "Bravo");
    assertTrue(store.snapshot().invitations().isEmpty());
  }

  @TempDir Path dir;
  Store store;
  ClanService svc;
  TestClock clock;
  ClanService.Actor a = new ClanService.Actor(UUID.randomUUID(), "Alice", false),
      b = new ClanService.Actor(UUID.randomUUID(), "Bob", false),
      c = new ClanService.Actor(UUID.randomUUID(), "Cara", false);

  static class TestClock extends Clock {
    long now = 1800000000000L;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return Instant.ofEpochMilli(now);
    }
  }

  @BeforeEach
  void setup() {
    clock = new TestClock();
    store = new Store(dir.resolve("test.db"));
    svc = new ClanService(store, Settings.defaults(), clock);
  }

  @AfterEach
  void close() {
    store.close();
  }

  CompletableFuture<String> run(
      ClanService.Actor actor, String action, UUID target, String... args) {
    return svc.execute(actor, action, args, target, "Target");
  }

  void ok(ClanService.Actor actor, String action, String... args) {
    run(actor, action, null, args).join();
  }

  void fails(ClanService.Actor actor, String action, UUID target, String... args) {
    assertThrows(CompletionException.class, () -> run(actor, action, target, args).join());
  }

  void member(ClanService.Actor leader, ClanService.Actor member, String clan) {
    run(leader, "invite", member.id(), member.name()).join();
    ok(member, "accept", clan);
  }

  @Test
  void concurrentCaseInsensitiveCreation() {
    var x = run(a, "create", null, "Alpha");
    var y = run(b, "create", null, "aLPHA");
    long success =
        List.of(x, y).stream()
            .map(f -> f.handle((v, e) -> e == null).join())
            .filter(Boolean::booleanValue)
            .count();
    assertEquals(1, success);
    assertEquals(1, store.snapshot().clans().size());
  }

  @Test
  void concurrentJoiningDifferentClans() {
    ok(a, "create", "Alpha");
    ok(b, "create", "Bravo");
    ok(a, "open");
    ok(b, "open");
    var x = run(c, "join", null, "Alpha");
    var y = run(c, "join", null, "Bravo");
    assertEquals(
        1,
        List.of(x, y).stream()
            .map(f -> f.handle((v, e) -> e == null).join())
            .filter(Boolean::booleanValue)
            .count());
    Store.validate(store.snapshot());
  }

  @Test
  void ranksAndAtomicLeadership() {
    ok(a, "create", "Alpha");
    member(a, b, "Alpha");
    member(a, c, "Alpha");
    fails(b, "disband", null);
    run(a, "promote", b.id(), "Bob").join();
    fails(b, "kick", a.id(), "Alice");
    fails(b, "promote", c.id(), "Cara");
    run(a, "transfer", b.id(), "Bob").join();
    assertEquals(Rank.LEADER, store.snapshot().clan(a.id()).members().get(a.id()).rank());
    ok(a, "confirm");
    Clan clan = store.snapshot().clan(a.id());
    assertEquals(Rank.ELDER, clan.members().get(a.id()).rank());
    assertEquals(Rank.LEADER, clan.members().get(b.id()).rank());
    Store.validate(store.snapshot());
  }

  @Test
  void invitationExpiryAndOpenClosedLimit() {
    ok(a, "create", "Alpha");
    fails(b, "join", null, "Alpha");
    run(a, "invite", b.id(), "Bob").join();
    clock.now += 600001;
    fails(b, "accept", null, "Alpha");
    ok(a, "open");
    ok(b, "join", "Alpha");
    ok(a, "close");
    fails(c, "join", null, "Alpha");
    Settings d = Settings.defaults();
    svc.settings =
        new Settings(
            3,
            16,
            100,
            2,
            d.inviteMillis(),
            d.peaceMillis(),
            d.renameMillis(),
            d.reserved(),
            d.timezone(),
            d.dateFormat());
    run(a, "invite", c.id(), "Cara").join();
    fails(c, "accept", null, "Alpha");
    ok(a, "open");
    fails(c, "join", null, "Alpha");
  }

  @Test
  void peaceIsMutualAndExpires() {
    ok(a, "create", "Alpha");
    ok(b, "create", "Bravo");
    ok(a, "enemy", "Bravo");
    assertTrue(
        store.snapshot().clan(b.id()).enemies().contains(store.snapshot().clan(a.id()).id()));
    ok(a, "peace", "Bravo");
    assertFalse(store.snapshot().clan(a.id()).enemies().isEmpty());
    fails(a, "peaceaccept", null, "Bravo");
    ok(b, "peacedeny", "Alpha");
    assertFalse(store.snapshot().clan(a.id()).enemies().isEmpty());
    ok(a, "peace", "Bravo");
    clock.now += 86400001;
    fails(b, "peaceaccept", null, "Alpha");
    ok(a, "peace", "Bravo");
    ok(b, "peaceaccept", "Alpha");
    assertTrue(store.snapshot().clan(a.id()).enemies().isEmpty());
    assertTrue(store.snapshot().clan(b.id()).enemies().isEmpty());
  }

  @Test
  void restartAndDisbandCleanup() {
    ok(a, "create", "Alpha");
    ok(b, "create", "Bravo");
    ok(a, "enemy", "Bravo");
    ok(a, "peace", "Bravo");
    run(a, "invite", c.id(), "Cara").join();
    Snapshot before = store.snapshot();
    store.close();
    store = new Store(dir.resolve("test.db"));
    svc = new ClanService(store, Settings.defaults(), clock);
    assertEquals(before, store.snapshot());
    ok(a, "rename", "Renamed");
    assertEquals(before.clan(a.id()).id(), store.snapshot().clan(a.id()).id());
    ok(a, "disband");
    ok(a, "confirm");
    assertNull(store.snapshot().clan(a.id()));
    assertTrue(store.snapshot().invitations().isEmpty());
    assertTrue(store.snapshot().peace().isEmpty());
    assertTrue(store.snapshot().clan(b.id()).enemies().isEmpty());
  }

  @Test
  void confirmationRevalidatesAndExpires() {
    ok(a, "create", "Alpha");
    member(a, b, "Alpha");
    run(a, "transfer", b.id(), "Bob").join();
    ok(b, "leave");
    fails(a, "confirm", null);
    ok(a, "disband");
    clock.now += 30001;
    fails(a, "confirm", null);
    assertNotNull(store.snapshot().clan(a.id()));
  }

  @Test
  void elderCannotKickElderAndMayLeave() {
    ok(a, "create", "Alpha");
    member(a, b, "Alpha");
    member(a, c, "Alpha");
    run(a, "promote", b.id(), "Bob").join();
    run(a, "promote", c.id(), "Cara").join();
    fails(b, "kick", c.id(), "Cara");
    ok(b, "leave");
    assertNull(store.snapshot().clan(b.id()));
  }

  @Test
  void validationAndFailedWritesDoNotPublish() {
    fails(a, "create", null, "<bad>");
    ok(a, "create", "Alpha");
    fails(a, "motto", null, "&cBad");
    Snapshot before = store.snapshot();
    assertThrows(
        CompletionException.class,
        () ->
            store
                .mutate(
                    st -> {
                      st.clans.put(
                          UUID.randomUUID(),
                          new Clan(
                              UUID.randomUUID(), "Wrong", "", 0, 0, false, Map.of(), Set.of()));
                      return null;
                    })
                .join());
    assertSame(before, store.snapshot());
  }

  @Test
  void staffOverridesAreSeparateFromNormalRankChecks() {
    ok(a, "create", "Alpha");
    member(a, b, "Alpha");
    fails(b, "rename", null, "Other");
    var staff = new ClanService.Actor(b.id(), b.name(), true);
    run(staff, "rename", null, "Alpha", "Other").join();
    assertNotNull(store.snapshot().named("Other"));
    fails(b, "disband", null);
    run(staff, "setleader", b.id(), "Other", "Bob").join();
    assertEquals(Rank.LEADER, store.snapshot().clan(b.id()).members().get(b.id()).rank());
    fails(staff, "remove", b.id(), "Bob");
  }

  @Test
  void renameCooldownAndInvitationsSurviveRenaming() {
    ok(a, "create", "Alpha");
    run(a, "invite", b.id(), "Bob").join();
    UUID id = store.snapshot().clan(a.id()).id();
    ok(a, "rename", "Other");
    fails(a, "rename", null, "Third");
    ok(b, "accept", "Other");
    assertEquals(id, store.snapshot().clan(b.id()).id());
    assertTrue(store.snapshot().invitations().isEmpty());
    clock.now += 86400001;
    ok(a, "rename", "Third");
  }

  @Test
  void cancelledAndSimultaneousConfirmations() {
    ok(a, "create", "Alpha");
    ok(a, "disband");
    ok(a, "cancel");
    fails(a, "confirm", null);
    ok(a, "disband");
    var x = run(a, "confirm", null);
    var y = run(a, "confirm", null);
    assertEquals(
        1,
        List.of(x, y).stream()
            .map(f -> f.handle((v, e) -> e == null).join())
            .filter(Boolean::booleanValue)
            .count());
    assertTrue(store.snapshot().clans().isEmpty());
  }

  @Test
  void sqlConstraintsRejectLeaderDemotionOutsideTransaction() throws Exception {
    ok(a, "create", "Alpha");
    try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("test.db"))) {
      try (var stmt = db.createStatement()) {
        stmt.execute("PRAGMA foreign_keys=ON");
        assertThrows(
            java.sql.SQLException.class,
            () -> stmt.executeUpdate("UPDATE members SET rank='MEMBER' WHERE rank='LEADER'"));
      }
    }
  }

  @Test
  void databaseBusyFailureKeepsCacheAndWriterRecovers() throws Exception {
    ok(a, "create", "Alpha");
    Snapshot before = store.snapshot();
    try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("test.db"))) {
      db.setAutoCommit(false);
      try (var stmt = db.createStatement()) {
        stmt.executeUpdate("UPDATE clans SET motto='uncommitted external lock'");
        assertThrows(CompletionException.class, () -> run(a, "motto", null, "New motto").join());
        assertSame(before, store.snapshot());
        db.rollback();
      }
    }
    ok(a, "motto", "Recovered");
    assertEquals("Recovered", store.snapshot().clan(a.id()).motto());
  }
}
