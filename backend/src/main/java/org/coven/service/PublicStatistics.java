package org.coven.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Converts frozen or live counters to a percentage-only response without mutating storage. */
public final class PublicStatistics {
  private PublicStatistics() {}

  public static double percent(double value, double denominator) {
    return denominator > 0 ? Math.min(100, 100 * value / denominator) : 0;
  }

  public static ObjectNode project(JsonNode source) {
    ObjectNode result = source.deepCopy();
    double total = source.path("total").asDouble();
    for (String dimension : List.of("region", "country")) {
      var counters = new LinkedHashMap<String, double[]>();
      for (var row : source.path("activity")) {
        var counts = counters.computeIfAbsent(row.path(dimension).asText(), k -> new double[2]);
        counts[0] += row.path("verified").asDouble();
        counts[1] += row.path("eligible").asDouble();
      }
      var activity = result.putArray(dimension + "Activity");
      counters.forEach(
          (key, counts) ->
              activity
                  .addObject()
                  .put(dimension, key)
                  .put("percentage", percent(counts[0], counts[1]))
                  .put("hasEligible", counts[1] > 0));
    }
    double eligible = 0;
    for (var clan : source.path("clans")) eligible += clan.path("eligible").asDouble();
    result.put("percentage", percent(source.path("verified").asDouble(), eligible));
    result.put("hasParticipants", total > 0);
    scrub(result, total);
    return result;
  }

  private static void scrub(JsonNode node, double total) {
    if (node instanceof ObjectNode object) {
      if (object.has("eligible")) {
        object.put(
            "percentage",
            percent(object.path("verified").asDouble(), object.path("eligible").asDouble()));
        object.put("hasEligible", object.path("eligible").asDouble() > 0);
      }
      if (object.has("members"))
        object.put("share", percent(object.path("members").asDouble(), total));
      if (object.has("n")) object.put("share", percent(object.path("n").asDouble(), total));
      object.remove(List.of("total", "verified", "eligible", "members", "n"));
      object.elements().forEachRemaining(child -> scrub(child, total));
    } else if (node.isArray()) node.forEach(child -> scrub(child, total));
  }
}
