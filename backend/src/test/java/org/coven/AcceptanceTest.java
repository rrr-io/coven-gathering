package org.coven;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.coven.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AcceptanceTest.TimeConfig.class)
class AcceptanceTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired MutableClock clock;
  @Autowired ConfigurationService config;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  CertificateValidator certificates;

  static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-11T00:00:00Z").minusSeconds(3600);

    void at(String time) {
      now = ZonedDateTime.parse(time).toInstant();
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  private String admin() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString(
                "admin:integration-tests-only-password".getBytes(StandardCharsets.UTF_8));
  }

  private Cookie session() throws Exception {
    var r = mvc.perform(get("/api/coven")).andExpect(status().isOk()).andReturn();
    return MockCookie.parse(Objects.requireNonNull(r.getResponse().getHeader("Set-Cookie")));
  }

  private JsonNode state(Cookie cookie) throws Exception {
    JsonNode result =
        mapper.readTree(
            mvc.perform(get("/api/coven").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertNoCounters(result.path("stats"));
    assertNoCounters(result.path("history"));
    return result;
  }

  private void assertNoCounters(JsonNode node) {
    if (node.isObject()) {
      for (String field : List.of("total", "verified", "eligible", "members", "n"))
        assertFalse(node.has(field), "Public counter leaked: " + field);
    }
    if (node.isContainerNode()) node.forEach(this::assertNoCounters);
  }

  private JsonNode postJson(Cookie cookie, Object body, int expected, boolean admin)
      throws Exception {
    var req =
        post("/api/coven")
            .cookie(cookie)
            .contentType("application/json")
            .content(mapper.writeValueAsBytes(body));
    if (admin) req.header("Authorization", admin());
    return mapper.readTree(
        mvc.perform(req)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode quiz(Cookie cookie, String country, int answer) throws Exception {
    return postJson(
        cookie,
        Map.of("action", "quiz", "country", country, "answers", Collections.nCopies(8, answer)),
        200,
        false);
  }

  @Test
  void completeAcceptanceAndSecurity() throws Exception {
    clock.at("2026-10-11T08:00:00+09:00");
    Cookie member = session();
    assertTrue(((MockCookie) member).isHttpOnly());
    var s = state(member);
    assertFalse(s.path("stats").path("hasParticipants").asBoolean());
    assertEquals(0, s.path("stats").path("percentage").asDouble());
    assertTrue(s.path("config").path("scoring").isMissingNode());
    assertTrue(s.path("participant").isNull());
    // Selecting a country happens exclusively in React; no participant mutation exists.
    assertEquals(0, state(member).path("stats").path("people").size());
    var q = quiz(member, "PH", 0);
    assertEquals("cullen", q.path("clan").asText());
    assertFalse(state(member).path("stats").path("hasParticipants").asBoolean());
    var confirm = Map.of("action", "confirm", "resultId", q.path("resultId").asText());
    postJson(member, confirm, 200, false);
    postJson(member, confirm, 200, false);
    s = state(member);
    assertTrue(s.path("stats").path("hasParticipants").asBoolean());
    assertEquals(100, s.path("stats").path("clans").get(0).path("share").asDouble());
    assertEquals("PH", s.path("participant").path("country").asText());
    assertEquals("SEA", s.path("participant").path("region").asText());
    assertEquals(0, s.path("stats").path("percentage").asDouble());
    Cookie outsider = session();
    postJson(outsider, confirm, 400, false);
    postJson(outsider, Map.of("action", "admin"), 401, false);
    mvc.perform(
            post("/api/coven")
                .cookie(member)
                .header("Origin", "https://untrusted.invalid")
                .contentType("application/json")
                .content("{\"action\":\"confirm\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(multipart("/api/coven").cookie(member).param("url", "https://example.test/proof"))
        .andExpect(status().isBadRequest());
    clock.at("2026-10-11T09:30:00+09:00");
    org.mockito.Mockito.when(certificates.validate(org.mockito.ArgumentMatchers.any(byte[].class)))
        .thenReturn("a".repeat(64));
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6xkAAAAASUVORK5CYII=");
    mvc.perform(
            multipart("/api/coven")
                .file(new MockMultipartFile("file", "proof.png", "image/png", png))
                .cookie(member))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"));
    s = state(member);
    String proof = s.path("myProofs").get(0).path("id").asText();
    mvc.perform(get("/api/proof/" + proof).cookie(outsider)).andExpect(status().isNotFound());
    mvc.perform(get("/api/proof/" + proof).cookie(member)).andExpect(status().isNotFound());
    postJson(member, Map.of("action", "review", "id", proof, "status", "VERIFIED"), 400, true);
    s = state(member);
    assertEquals(100, s.path("stats").path("percentage").asDouble());
    var c = s.path("stats").path("schedules").get(0).path("clans").get(0);
    assertEquals(100, c.path("percentage").asDouble());
    assertTrue(c.path("hasEligible").asBoolean());
    for (int i = 1; i < 4; i++)
      assertEquals(0, s.path("stats").path("clans").get(i).path("percentage").asDouble());
    mvc.perform(multipart("/api/coven").cookie(member).param("url", "https://example.test/second"))
        .andExpect(status().isConflict());
    var late = quiz(outsider, "IT", 1);
    postJson(
        outsider,
        Map.of("action", "confirm", "resultId", late.path("resultId").asText()),
        200,
        false);
    mvc.perform(multipart("/api/coven").cookie(outsider).param("url", "https://example.test/late"))
        .andExpect(status().isBadRequest());
    assertEquals(
        0,
        state(member)
            .path("stats")
            .path("schedules")
            .get(0)
            .path("clans")
            .get(1)
            .path("percentage")
            .asInt());
    clock.at("2026-10-11T12:00:00+09:00");
    mvc.perform(
            multipart("/api/coven")
                .file(new MockMultipartFile("file", "proof.png", "image/png", png))
                .cookie(member))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DUPLICATE"));
    assertEquals(100.0 / 3, state(member).path("stats").path("percentage").asDouble(), 0.001);
    // Invalid and duplicate certificates do not consume the participant's slot.
    org.mockito.Mockito.when(certificates.validate(org.mockito.ArgumentMatchers.any(byte[].class)))
        .thenThrow(ParticipationService.bad("Certificate not recognized."));
    mvc.perform(
            multipart("/api/coven")
                .file(new MockMultipartFile("file", "screen.png", "image/png", png))
                .cookie(outsider))
        .andExpect(status().isBadRequest());
    assertTrue(state(outsider).path("myProofs").isEmpty());
    org.mockito.Mockito.doReturn("b".repeat(64))
        .when(certificates)
        .validate(org.mockito.ArgumentMatchers.any(byte[].class));
    mvc.perform(
            multipart("/api/coven")
                .file(new MockMultipartFile("file", "certificate.png", "image/png", png))
                .cookie(outsider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"));
    // Config change must not rewrite the already frozen daily schedule.
    var updated = config.current();
    ((ObjectNode) updated.path("schedules").get(0)).put("start", "08:30");
    postJson(member, Map.of("action", "config", "config", updated), 200, true);
    assertEquals("09:00", state(member).path("schedules").get(0).path("start").asText());
    clock.at("2026-10-12T00:00:00+09:00");
    s = state(member);
    assertEquals("2026-10-12", s.path("day").asText());
    assertTrue(s.path("stats").path("hasParticipants").asBoolean());
    assertEquals(0, s.path("stats").path("percentage").asDouble());
    assertEquals(0, s.path("myProofs").size());
    assertEquals("08:30", s.path("schedules").get(0).path("start").asText());
    assertEquals(200.0 / 7, s.path("history").get(0).path("percentage").asDouble(), 0.001);
    var history = s.path("history").deepCopy();
    postJson(member, Map.of("action", "review", "id", proof, "status", "REJECTED"), 400, true);
    Cookie fresh = session();
    var fq = quiz(fresh, "JP", 2);
    postJson(
        fresh, Map.of("action", "confirm", "resultId", fq.path("resultId").asText()), 200, false);
    assertEquals(history, state(member).path("history"));
  }

  @Test
  void sortingAndBoundaries() {
    var scoring = config.current().path("scoring");
    for (int i = 0; i < 4; i++)
      assertEquals(
          GatheringRules.CLANS.get(i),
          GatheringRules.sort(mapper.valueToTree(Collections.nCopies(8, i)), scoring, null).clan());
    JsonNode answers = mapper.valueToTree(List.of(0, 0, 0, 0, 1, 1, 1, 1));
    assertEquals(List.of("cullen", "volturi"), GatheringRules.sort(answers, scoring, null).tied());
    assertEquals("volturi", GatheringRules.sort(answers, scoring, "volturi").clan());
    assertNull(GatheringRules.sort(answers, scoring, "denali").clan());
    assertEquals(
        LocalDate.of(2026, 10, 12), GatheringRules.day(Instant.parse("2026-10-11T15:00:00Z")));
    var windows =
        GatheringRules.windows(
            LocalDate.of(2026, 10, 11),
            config.current().path("schedules"),
            Instant.parse("2026-10-11T02:00:00Z").toEpochMilli());
    assertEquals("ENDED", windows.get(0).get("status"));
  }
}
