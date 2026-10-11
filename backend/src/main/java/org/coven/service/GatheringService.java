package org.coven.service;

import com.fasterxml.jackson.databind.*;
import java.time.Clock;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GatheringService {
  private final CovenRepository db;
  private final ConfigurationService config;
  private final RaceService race;
  private final Clock clock;

  public GatheringService(
      CovenRepository db, ConfigurationService config, RaceService race, Clock clock) {
    this.db = db;
    this.config = config;
    this.race = race;
    this.clock = clock;
  }

  @Transactional
  public Object action(String user, boolean admin, String adminName, JsonNode b) {
    var current = race.reconcile();
    var cfg = config.current();
    String action = b.path("action").asText();
    if (action.equals("quiz")) {
      if (!cfg.path("quiz").path("enabled").asBoolean())
        throw bad("Sorting is temporarily paused.");
      if (db.one("SELECT id FROM participants WHERE user_id=?", user) != null)
        throw bad("Your clan is already locked.");
      var country = config.country(b.path("country").asText());
      var sorted =
          RaceRules.sort(b.path("answers"), cfg.path("scoring"), b.path("tie").asText(null));
      if (sorted.clan() == null) {
        var result = new LinkedHashMap<String, Object>();
        result.put("clan", null);
        result.put("tied", sorted.tied());
        return result;
      }
      String id = UUID.randomUUID().toString();
      long count =
          CovenRepository.number(
              db.one("SELECT COUNT(*) AS n FROM quiz_results WHERE user_id=?", user).get("n"));
      db.execute(
          "INSERT INTO quiz_results(id,user_id,country,clan,payload,version,created_at,retake)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          id,
          user,
          country.path("id").asText(),
          sorted.clan(),
          config.json(b),
          cfg.path("quiz").path("version").asInt(),
          clock.millis(),
          count);
      return Map.of("resultId", id, "clan", sorted.clan());
    }
    if (action.equals("confirm")) {
      var quiz =
          db.one(
              "SELECT * FROM quiz_results WHERE id=? AND user_id=?",
              b.path("resultId").asText(),
              user);
      if (quiz == null) throw bad("Complete the quiz first.");
      if (db.one("SELECT id FROM participants WHERE user_id=?", user) == null) {
        var country = config.country((String) quiz.get("country"));
        var override = cfg.path("countries").path(country.path("id").asText());
        long now = clock.millis();
        db.execute(
            "INSERT INTO"
                + " participants(id,user_id,created_at,country,region,timezone,clan,quiz_result_id,clan_confirmed_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),
            user,
            now,
            country.path("id").asText(),
            override.path("region").asText(country.path("region").asText()),
            override.path("timezone").asText(country.path("timezone").asText()),
            quiz.get("clan"),
            quiz.get("id"),
            now);
      }
      return Map.of("ok", true);
    }
    if (!admin)
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Administrator credentials required.");
    if (action.equals("admin"))
      return Map.of(
          "config",
          cfg,
          "proofs",
          db.rows(
              "SELECT id,race_day_id,schedule_id,clan,status,submitted_at,proof_url,object_key FROM"
                  + " proofs ORDER BY submitted_at DESC LIMIT 200"),
          "coordination",
          db.rows(
              "SELECT country,region,clan,COUNT(*) AS members FROM participants WHERE active=TRUE"
                  + " GROUP BY country,region,clan"));
    if (action.equals("review")) {
      String status = b.path("status").asText();
      if (!Set.of("VERIFIED", "REJECTED", "DUPLICATE").contains(status))
        throw bad("Invalid review status.");
      var proof = db.one("SELECT * FROM proofs WHERE id=?", b.path("id").asText());
      if (proof == null) throw bad("Proof not found.");
      if (!proof.get("race_day_id").equals(current.day().toString())
          || clock.millis() >= CovenRepository.number(current.record().get("end_at")))
        throw bad("Archived race days cannot be changed.");
      if (!proof.get("status").equals("PENDING"))
        throw bad("This proof has already been reviewed.");
      if (status.equals("VERIFIED")
          && db.one("SELECT id FROM proofs WHERE verified_hash=?", proof.get("hash")) != null)
        status = "DUPLICATE";
      db.execute(
          "UPDATE proofs SET status=?,verified_hash=?,verified_at=?,reviewed_by=? WHERE id=?",
          status,
          status.equals("VERIFIED") ? proof.get("hash") : null,
          status.equals("VERIFIED") ? clock.millis() : null,
          adminName,
          proof.get("id"));
      audit(adminName, "proof_review", Map.of("id", proof.get("id"), "status", status));
      return Map.of("ok", true, "status", status);
    }
    if (action.equals("config")) {
      config.save(b.path("config"));
      audit(adminName, "configuration", b.path("config"));
      return Map.of("ok", true);
    }
    throw bad("Unknown action.");
  }

  private void audit(String actor, String action, Object detail) {
    db.execute(
        "INSERT INTO admin_audit(id,actor,action,created_at,detail) VALUES(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor,
        action,
        clock.millis(),
        config.json(detail));
  }

  public static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
