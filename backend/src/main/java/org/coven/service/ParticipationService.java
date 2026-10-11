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
public class ParticipationService {
  private final CovenRepository db;
  private final ConfigurationService config;
  private final GatheringService gathering;
  private final Clock clock;

  public ParticipationService(
      CovenRepository db, ConfigurationService config, GatheringService gathering, Clock clock) {
    this.db = db;
    this.config = config;
    this.gathering = gathering;
    this.clock = clock;
  }

  @Transactional
  public Object action(String user, boolean admin, String adminName, JsonNode b) {
    var current = gathering.reconcile();
    var cfg = config.current();
    String action = b.path("action").asText();
    if (action.equals("quiz")) {
      if (!cfg.path("quiz").path("enabled").asBoolean())
        throw bad("Sorting is temporarily paused.");
      if (db.one("SELECT id FROM participants WHERE user_id=?", user) != null)
        throw bad("Your clan is already locked.");
      var country = config.country(b.path("country").asText());
      var sorted =
          GatheringRules.sort(b.path("answers"), cfg.path("scoring"), b.path("tie").asText(null));
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
    if (action.equals("admin")) {
      var rows =
          db.rows(
              "SELECT country,region,clan,COUNT(*) AS members FROM participants WHERE active=TRUE"
                  + " GROUP BY country,region,clan");
      double total = rows.stream().mapToDouble(r -> CovenRepository.number(r.get("members"))).sum();
      var coordination =
          rows.stream()
              .map(
                  row -> {
                    var safe = new LinkedHashMap<>(row);
                    safe.put(
                        "share",
                        PublicStatistics.percent(
                            CovenRepository.number(safe.remove("members")), total));
                    return safe;
                  })
              .toList();
      return Map.of("config", cfg, "coordination", coordination);
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
