package org.coven.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CovenRepository {
  private final JdbcTemplate jdbc;

  public CovenRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var r = rows(sql, args);
    return r.isEmpty() ? null : r.getFirst();
  }

  public int execute(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public void lockGathering() {
    jdbc.queryForObject(
        "SELECT setting_value FROM settings WHERE setting_key='gathering_mutex' FOR UPDATE",
        String.class);
  }

  public static long number(Object n) {
    return n == null ? 0 : ((Number) n).longValue();
  }
}
