package org.coven.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationService {
  private final CovenRepository db;
  private final ObjectMapper mapper;
  private final JsonNode defaults, countries;

  public ConfigurationService(CovenRepository db, ObjectMapper mapper) throws Exception {
    this.db = db;
    this.mapper = mapper;
    defaults = mapper.readTree(new ClassPathResource("config.json").getInputStream());
    countries = mapper.readTree(new ClassPathResource("countries.json").getInputStream());
  }

  public JsonNode parse(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public ObjectNode current() {
    var row = db.one("SELECT setting_value FROM settings WHERE setting_key='config'");
    return (ObjectNode)
        (row == null ? defaults.deepCopy() : parse((String) row.get("setting_value")));
  }

  public JsonNode country(String id) {
    for (var c : countries) if (c.path("id").asText().equals(id)) return c;
    throw new IllegalArgumentException("Choose a valid country.");
  }

  public JsonNode countries() {
    return countries;
  }

  public static boolean validUrl(String value) {
    try {
      URI u = URI.create(value);
      return Set.of("https", "http").contains(u.getScheme())
          && u.getHost() != null
          && u.getUserInfo() == null;
    } catch (Exception e) {
      return false;
    }
  }

  public void validate(JsonNode c) {
    for (String key :
        List.of(
            "general",
            "clans",
            "quiz",
            "questions",
            "scoring",
            "schedules",
            "countries",
            "regions",
            "guide",
            "content",
            "banners",
            "admin"))
      if (!c.has(key)) throw new IllegalArgumentException("Missing configuration: " + key);
    if (c.path("general").path("title").asText().isBlank()
        || !c.path("quiz").path("enabled").isBoolean())
      throw new IllegalArgumentException("A title and quiz enabled flag are required.");
    if (c.path("clans").size() != 4) throw new IllegalArgumentException("Keep four clans.");
    for (int i = 0; i < 4; i++) {
      var clan = c.path("clans").get(i);
      if (!clan.path("id").asText().equals(RaceRules.CLANS.get(i))
          || clan.path("name").asText().isBlank()
          || !clan.path("color").asText().matches("#[0-9a-fA-F]{6}"))
        throw new IllegalArgumentException("Keep stable clan IDs, names and valid hex colors.");
    }
    if (c.path("questions").size() != 8 || c.path("scoring").size() != 8)
      throw new IllegalArgumentException("Eight quiz questions and scoring rows are required.");
    for (var q : c.path("questions")) {
      if (q.path("title").asText().isBlank()
          || q.path("text").asText().isBlank()
          || q.path("answers").size() != 4)
        throw new IllegalArgumentException("Each question needs text and four answers.");
      for (var a : q.path("answers"))
        if (!a.isTextual() || a.asText().isBlank())
          throw new IllegalArgumentException("Answers must be nonempty text.");
    }
    for (var q : c.path("scoring")) {
      if (q.size() != 4) throw new IllegalArgumentException("Scoring shape is 8 × 4 × 4.");
      for (var a : q) {
        if (a.size() != 4) throw new IllegalArgumentException("Scoring shape is 8 × 4 × 4.");
        for (var n : a)
          if (!n.isNumber()
              || !Double.isFinite(n.asDouble())
              || n.asDouble() < 0
              || n.asDouble() > 100) throw new IllegalArgumentException("Invalid scoring points.");
      }
    }
    if (c.path("schedules").size() != 4)
      throw new IllegalArgumentException("Four schedules are required.");
    LocalTime end = LocalTime.MIN;
    int id = 0;
    for (var s : c.path("schedules")) {
      var a = LocalTime.parse(s.path("start").asText());
      var b = LocalTime.parse(s.path("end").asText());
      if (s.path("id").asInt() != ++id || a.isBefore(end) || !a.isBefore(b))
        throw new IllegalArgumentException(
            "Schedules must have IDs 1–4, be ordered and not overlap.");
      end = b;
    }
    c.path("countries")
        .fields()
        .forEachRemaining(
            e -> {
              country(e.getKey());
              String region = e.getValue().path("region").asText();
              if (region.isBlank() || !c.path("regions").has(region))
                throw new IllegalArgumentException("Country overrides need a configured region.");
              if (e.getValue().has("timezone")) ZoneId.of(e.getValue().path("timezone").asText());
            });
    if (!c.path("guide").path("sources").isArray())
      throw new IllegalArgumentException("Guide sources must be an array.");
    for (var u : c.path("guide").path("sources"))
      if (!validUrl(u.asText()))
        throw new IllegalArgumentException("Guide sources must be valid HTTP(S) URLs.");
    if (json(c).length() > 100000) throw new IllegalArgumentException("Configuration too large.");
  }

  public void save(JsonNode value) {
    validate(value);
    ObjectNode c = value.deepCopy();
    ((ObjectNode) c.path("quiz"))
        .put("version", current().path("quiz").path("version").asInt() + 1);
    if (db.execute("UPDATE settings SET setting_value=? WHERE setting_key='config'", json(c)) == 0)
      db.execute("INSERT INTO settings(setting_key,setting_value) VALUES('config',?)", json(c));
  }
}
