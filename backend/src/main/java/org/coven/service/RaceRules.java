package org.coven.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

public final class RaceRules {
  public static final ZoneId KST = ZoneId.of("Asia/Seoul");
  public static final List<String> CLANS = List.of("cullen", "volturi", "denali", "egyptian");

  private RaceRules() {}

  public static LocalDate day(Instant now) {
    return now.atZone(KST).toLocalDate();
  }

  public static long start(LocalDate day) {
    return day.atStartOfDay(KST).toInstant().toEpochMilli();
  }

  public static List<Map<String, Object>> windows(LocalDate day, JsonNode defs, long now) {
    var result = new ArrayList<Map<String, Object>>();
    for (var d : defs) {
      var s =
          LocalDateTime.of(day, LocalTime.parse(d.path("start").asText()))
              .atZone(KST)
              .toInstant()
              .toEpochMilli();
      var e =
          LocalDateTime.of(day, LocalTime.parse(d.path("end").asText()))
              .atZone(KST)
              .toInstant()
              .toEpochMilli();
      result.add(
          Map.of(
              "id",
              d.path("id").asInt(),
              "name",
              d.path("name").asText(),
              "start",
              d.path("start").asText(),
              "end",
              d.path("end").asText(),
              "startAt",
              s,
              "endAt",
              e,
              "status",
              now < s ? "UPCOMING" : now < e ? "LIVE" : "ENDED"));
    }
    return result;
  }

  public record Sorting(String clan, List<String> tied) {}

  public static Sorting sort(JsonNode answers, JsonNode scoring, String tie) {
    if (!answers.isArray() || answers.size() != 8)
      throw new IllegalArgumentException("Answer all eight questions.");
    double[] scores = new double[4];
    for (int q = 0; q < 8; q++) {
      var answer = answers.get(q);
      if (!answer.isIntegralNumber() || answer.asInt() < 0 || answer.asInt() > 3)
        throw new IllegalArgumentException("Invalid answer.");
      for (int c = 0; c < 4; c++) scores[c] += scoring.get(q).get(answer.asInt()).get(c).asDouble();
    }
    double max = Arrays.stream(scores).max().orElse(0);
    var tied = new ArrayList<String>();
    for (int i = 0; i < 4; i++) if (scores[i] == max) tied.add(CLANS.get(i));
    if (tied.size() == 1) return new Sorting(tied.getFirst(), List.of());
    if (tie != null && tied.contains(tie)) return new Sorting(tie, List.of());
    return new Sorting(null, tied);
  }
}
