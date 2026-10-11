package org.coven.security;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessService {
  private final CovenRepository db;
  private final Clock clock;
  private final String adminUser, adminPassword;
  private final boolean secure;

  public AccessService(
      CovenRepository db,
      Clock clock,
      @Value("${admin.username}") String user,
      @Value("${admin.password}") String password,
      @Value("${app.cookie-secure}") boolean secure) {
    if (password == null || password.length() < 16)
      throw new IllegalStateException("ADMIN_PASSWORD must contain at least 16 characters.");
    this.db = db;
    this.clock = clock;
    this.adminUser = user;
    this.adminPassword = password;
    this.secure = secure;
  }

  public String session(HttpServletRequest req, HttpServletResponse res) {
    String raw = null;
    if (req.getCookies() != null)
      for (var c : req.getCookies()) if (c.getName().equals("coven_session")) raw = c.getValue();
    if (raw != null && raw.matches("[A-Za-z0-9_-]{43}")) {
      String hash = hash(raw.getBytes(StandardCharsets.UTF_8));
      if (db.one(
              "SELECT token_hash FROM browser_sessions WHERE token_hash=? AND expires_at>?",
              hash,
              clock.millis())
          != null) return hash;
    }
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    String hash = hash(raw.getBytes(StandardCharsets.UTF_8));
    db.execute(
        "INSERT INTO browser_sessions(token_hash,expires_at) VALUES (?,?)",
        hash,
        clock.millis() + Duration.ofDays(365).toMillis());
    res.addHeader(
        "Set-Cookie",
        ResponseCookie.from("coven_session", raw)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api")
            .maxAge(Duration.ofDays(365))
            .build()
            .toString());
    return hash;
  }

  public boolean isAdmin(HttpServletRequest req) {
    try {
      String auth = req.getHeader("Authorization");
      if (auth == null || !auth.startsWith("Basic ")) return false;
      byte[] actual = Base64.getDecoder().decode(auth.substring(6));
      byte[] expected = (adminUser + ":" + adminPassword).getBytes(StandardCharsets.UTF_8);
      return MessageDigest.isEqual(
          MessageDigest.getInstance("SHA-256").digest(actual),
          MessageDigest.getInstance("SHA-256").digest(expected));
    } catch (Exception e) {
      return false;
    }
  }

  public void requireAdmin(HttpServletRequest req) {
    if (!isAdmin(req))
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Administrator credentials required.");
  }

  public String adminName() {
    return adminUser;
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
