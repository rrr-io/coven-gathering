package org.coven.service;

import static org.coven.repository.CovenRepository.number;
import static org.coven.service.GatheringRules.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GatheringService {
  private final CovenRepository db;
  private final ConfigurationService config;
  private final Clock clock;

  public GatheringService(CovenRepository db, ConfigurationService config, Clock clock) {
    this.db = db;
    this.config = config;
    this.clock = clock;
  }

  public record Gathering(LocalDate day, JsonNode config, Map<String, Object> record) {}

  // All mutation callers hold this same DB row lock, including multiple JVMs.
  @Transactional
  public Gathering reconcile() {
    db.lockGathering();
    LocalDate today = day(clock.instant());
    var currentConfig = config.current();
    var latest = db.one("SELECT id FROM daily_gatherings ORDER BY id DESC LIMIT 1");
    LocalDate first = latest == null ? today : LocalDate.parse((String) latest.get("id"));
    for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
      String id = day.toString();
      var row = db.one("SELECT * FROM daily_gatherings WHERE id=?", id);
      if (row == null) {
        db.execute(
            "INSERT INTO daily_gatherings(id,start_at,end_at,config) VALUES(?,?,?,?)",
            id,
            start(day),
            start(day.plusDays(1)),
            config.json(currentConfig));
        row = db.one("SELECT * FROM daily_gatherings WHERE id=?", id);
      }
      if (row.get("snapshot") != null) continue;
      JsonNode saved = config.parse((String) row.get("config"));
      for (var s : windows(day, saved.path("schedules"), clock.millis()))
        if (number(s.get("startAt")) <= clock.millis())
          for (String clan : CLANS) {
            String key = id + ":" + s.get("id") + ":" + clan;
            if (db.one("SELECT id FROM schedule_eligibility WHERE id=?", key) == null)
              db.execute(
                  "INSERT INTO schedule_eligibility(id,gathering_day_id,schedule_id,clan,eligible)"
                      + " SELECT ?,?,?,?,COUNT(*) FROM participants WHERE active=TRUE AND clan=?"
                      + " AND created_at < ?",
                  key,
                  id,
                  s.get("id"),
                  clan,
                  clan,
                  s.get("startAt"));
          }
      if (day.isBefore(today)) {
        var snapshot = aggregate(day, saved.path("schedules"), number(row.get("end_at")));
        db.execute(
            "UPDATE daily_gatherings SET snapshot=?,archived_at=? WHERE id=? AND snapshot IS NULL",
            config.json(snapshot),
            clock.millis(),
            id);
      }
    }
    var row = db.one("SELECT * FROM daily_gatherings WHERE id=?", today.toString());
    return new Gathering(today, config.parse((String) row.get("config")), row);
  }

  public Map<String, Object> aggregate(LocalDate day, JsonNode definitions, long cutoff) {
    String id = day.toString();
    var people =
        db.rows(
            "SELECT clan,country,region,COUNT(*) AS n FROM participants WHERE active=TRUE AND"
                + " created_at < ? GROUP BY clan,country,region",
            cutoff);
    var verified =
        db.rows(
            "SELECT clan,schedule_id,COUNT(*) AS n FROM proofs WHERE gathering_day_id=? AND"
                + " status='VERIFIED' AND verified_at < ? GROUP BY clan,schedule_id",
            id,
            cutoff);
    var eligible = db.rows("SELECT * FROM schedule_eligibility WHERE gathering_day_id=?", id);
    var schedules = new ArrayList<Map<String, Object>>();
    var totals = new ArrayList<Map<String, Object>>();
    for (var s : windows(day, definitions, cutoff - 1)) {
      var cs = new ArrayList<Map<String, Object>>();
      for (String c : CLANS) {
        long el =
            eligible.stream()
                .filter(
                    e ->
                        e.get("clan").equals(c)
                            && number(e.get("schedule_id")) == number(s.get("id")))
                .mapToLong(e -> number(e.get("eligible")))
                .sum();
        long v =
            verified.stream()
                .filter(
                    e ->
                        e.get("clan").equals(c)
                            && number(e.get("schedule_id")) == number(s.get("id")))
                .mapToLong(e -> number(e.get("n")))
                .sum();
        cs.add(Map.of("clan", c, "eligible", el, "verified", v));
      }
      var sw = new LinkedHashMap<>(s);
      sw.put("clans", cs);
      schedules.add(sw);
    }
    for (String c : CLANS) {
      long el =
          eligible.stream()
              .filter(e -> c.equals(e.get("clan")))
              .mapToLong(e -> number(e.get("eligible")))
              .sum();
      long v =
          verified.stream()
              .filter(e -> c.equals(e.get("clan")))
              .mapToLong(e -> number(e.get("n")))
              .sum();
      long members =
          people.stream()
              .filter(e -> c.equals(e.get("clan")))
              .mapToLong(e -> number(e.get("n")))
              .sum();
      totals.add(Map.of("clan", c, "eligible", el, "verified", v, "members", members));
    }
    var ws = windows(day, definitions, cutoff - 1);
    var current =
        ws.stream()
            .filter(s -> s.get("status").equals("LIVE"))
            .findFirst()
            .orElseGet(
                () ->
                    ws.stream()
                        .filter(s -> s.get("status").equals("ENDED"))
                        .reduce((a, b) -> b)
                        .orElse(null));
    var activity = new ArrayList<Map<String, Object>>();
    if (current != null) {
      var e =
          db.rows(
              "SELECT country,region,COUNT(*) AS eligible FROM participants WHERE active=TRUE AND"
                  + " created_at < ? GROUP BY country,region",
              current.get("startAt"));
      var v =
          db.rows(
              "SELECT country,region,COUNT(*) AS verified FROM proofs WHERE gathering_day_id=? AND"
                  + " schedule_id=? AND status='VERIFIED' AND verified_at < ? GROUP BY"
                  + " country,region",
              id,
              current.get("id"),
              cutoff);
      for (var row : e) {
        var a = new LinkedHashMap<>(row);
        a.put(
            "verified",
            v.stream()
                .filter(
                    x ->
                        x.get("country").equals(row.get("country"))
                            && x.get("region").equals(row.get("region")))
                .mapToLong(x -> number(x.get("verified")))
                .sum());
        activity.add(a);
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("day", id);
    result.put("total", people.stream().mapToLong(p -> number(p.get("n"))).sum());
    result.put("people", people);
    result.put("clans", totals);
    result.put("schedules", schedules);
    result.put("activity", activity);
    result.put("activityWindow", current == null ? null : current.get("name"));
    result.put("verified", verified.stream().mapToLong(v -> number(v.get("n"))).sum());
    return result;
  }

  @Transactional
  public Map<String, Object> state(String user, boolean admin) {
    Gathering gathering = reconcile();
    var result = new LinkedHashMap<String, Object>();
    var participant = db.one("SELECT * FROM participants WHERE user_id=? AND active=TRUE", user);
    var safeConfig = config.current();
    safeConfig.remove("scoring");
    result.put("serverNow", clock.millis());
    result.put("day", gathering.day.toString());
    result.put("config", safeConfig);
    result.put(
        "schedules", windows(gathering.day, gathering.config.path("schedules"), clock.millis()));
    result.put(
        "stats",
        PublicStatistics.project(
            config.parse(
                config.json(
                    aggregate(
                        gathering.day, gathering.config.path("schedules"), clock.millis() + 1)))));
    result.put("participant", participant == null ? null : safeParticipant(participant));
    result.put("admin", admin);
    result.put(
        "myProofs",
        participant == null
            ? List.of()
            : db.rows(
                "SELECT id,schedule_id,status,submitted_at FROM proofs WHERE participant_id=? AND"
                    + " gathering_day_id=?",
                participant.get("id"),
                gathering.day.toString()));
    result.put(
        "history",
        db
            .rows(
                "SELECT snapshot FROM daily_gatherings WHERE snapshot IS NOT NULL ORDER BY id DESC"
                    + " LIMIT 90")
            .stream()
            .map(r -> PublicStatistics.project(config.parse((String) r.get("snapshot"))))
            .toList());
    return result;
  }

  private Map<String, Object> safeParticipant(Map<String, Object> p) {
    var result = new LinkedHashMap<>(p);
    result.remove("user_id");
    return result;
  }
}
