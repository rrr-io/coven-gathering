package org.coven.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PublicStatisticsTest {
  @Test
  void weightsRegionsAndHidesEveryCounterWithoutChangingTheSnapshot() throws Exception {
    var mapper = new ObjectMapper();
    var source =
        mapper.readTree(
            """
            {"total":10,"verified":4,"clans":[{"clan":"cullen","verified":4,"eligible":10,"members":10}],
             "people":[{"country":"PH","region":"SEA","clan":"cullen","n":2}],
             "schedules":[{"clans":[{"verified":4,"eligible":10}]}],
             "activity":[{"country":"PH","region":"SEA","verified":1,"eligible":2},
                         {"country":"SG","region":"SEA","verified":3,"eligible":8}]}
            """);
    var original = source.deepCopy();
    var result = PublicStatistics.project(source);
    assertEquals(original, source);
    assertEquals(40, result.path("regionActivity").get(0).path("percentage").asDouble());
    assertEquals(20, result.path("people").get(0).path("share").asDouble());
    assertEquals(40, result.path("percentage").asDouble());
    for (String field : new String[] {"total", "verified", "eligible", "members", "n"})
      assertFalse(result.toString().contains("\"" + field + "\":"));
    assertEquals(0, PublicStatistics.percent(0, 0));
  }
}
