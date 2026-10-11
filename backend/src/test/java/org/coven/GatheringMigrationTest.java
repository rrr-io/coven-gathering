package org.coven;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class GatheringMigrationTest {
  @Test
  void upgradePreservesExistingMembersAndFrozenResults() throws Exception {
    String url = "jdbc:h2:mem:upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
    String snapshot = "{\"verified\":1,\"total\":1,\"note\":\"original race snapshot\"}";
    try (var conn = DriverManager.getConnection(url, "sa", "");
        var sql = conn.createStatement()) {
      sql.execute(
          "INSERT INTO daily_races VALUES ('2026-10-10',0,100,'{}','" + snapshot + "',100)");
      sql.execute("INSERT INTO quiz_results VALUES ('q','u','PH','cullen','{}',1,0,0)");
      sql.execute(
          "INSERT INTO"
              + " participants(id,user_id,created_at,country,region,timezone,clan,quiz_result_id,clan_confirmed_at)"
              + " VALUES ('p','u',0,'PH','SEA','Asia/Manila','cullen','q',0)");
      sql.execute("INSERT INTO schedule_eligibility VALUES ('s','2026-10-10',1,'cullen',1)");
      sql.execute(
          "INSERT INTO"
              + " proofs(id,race_day_id,schedule_id,participant_id,clan,country,region,submitted_at,object_key,hash,status,verified_at)"
              + " VALUES"
              + " ('a','2026-10-10',1,'p','cullen','PH','SEA',1,'legacy-image','a','VERIFIED',2)");
      sql.execute(
          "INSERT INTO"
              + " proofs(id,race_day_id,schedule_id,participant_id,clan,country,region,submitted_at,proof_url,hash,status)"
              + " VALUES"
              + " ('b','2026-10-10',2,'p','cullen','PH','SEA',1,'https://example.test/legacy','b','PENDING')");
      sql.execute("INSERT INTO settings VALUES ('config','{\"title\":\"THE COVEN RACE\"}')");
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (var conn = DriverManager.getConnection(url, "sa", "");
        var sql = conn.createStatement()) {
      try (var rows = sql.executeQuery("SELECT snapshot FROM daily_gatherings")) {
        assertTrue(rows.next());
        assertEquals(snapshot, rows.getString(1));
      }
      try (var rows = sql.executeQuery("SELECT country,clan FROM participants")) {
        assertTrue(rows.next());
        assertEquals("PH", rows.getString(1));
        assertEquals("cullen", rows.getString(2));
      }
      try (var rows =
          sql.executeQuery("SELECT gathering_day_id,eligible FROM schedule_eligibility")) {
        assertTrue(rows.next());
        assertEquals("2026-10-10", rows.getString(1));
        assertEquals(1, rows.getInt(2));
      }
      try (var rows = sql.executeQuery("SELECT status FROM proofs ORDER BY id")) {
        assertTrue(rows.next());
        assertEquals("VERIFIED", rows.getString(1));
        assertTrue(rows.next());
        assertEquals("REJECTED", rows.getString(1));
      }
      assertThrows(
          java.sql.SQLException.class, () -> sql.executeQuery("SELECT object_key FROM proofs"));
      try (var rows =
          sql.executeQuery("SELECT setting_value FROM settings WHERE setting_key='config'")) {
        assertTrue(rows.next());
        assertTrue(rows.getString(1).contains("GATHERING"));
      }
    }
  }
}
