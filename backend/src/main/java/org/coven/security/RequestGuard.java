package org.coven.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestGuard extends OncePerRequestFilter {
  private final Set<String> origins;
  private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

  public RequestGuard(@Value("${app.origins}") String origins) {
    this.origins = new HashSet<>(Arrays.asList(origins.split(",")));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request.getRequestURI().startsWith("/api")) {
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      String origin = request.getHeader("Origin");
      // Prototype: "*" disables the origin check (CSRF protection off).
      if (origin != null && !origins.contains("*") && !origins.contains(origin)) {
        response.sendError(403, "Origin not allowed");
        return;
      }
      if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
          && origin == null
          && "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
        response.sendError(403);
        return;
      }
      if (request.getHeader("Authorization")
          != null) { // Bound Basic-auth attempts per peer. Do not trust arbitrary X-Forwarded-For
                     // here.
        long minute = System.currentTimeMillis() / 60000;
        String ip = request.getRemoteAddr();
        if (attempts.size() > 10000) attempts.entrySet().removeIf(e -> e.getValue()[0] < minute);
        long[] count =
            attempts.compute(
                ip,
                (k, v) ->
                    v == null || v[0] != minute
                        ? new long[] {minute, 1}
                        : new long[] {minute, v[1] + 1});
        if (count[1] > 120) {
          response.sendError(429, "Too many administrator requests");
          return;
        }
      }
    }
    chain.doFilter(request, response);
  }
}
