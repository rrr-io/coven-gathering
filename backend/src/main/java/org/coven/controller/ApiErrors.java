package org.coven.controller;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> response(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("error", e.getReason() == null ? "Request failed." : e.getReason()));
  }

  @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
  public ResponseEntity<?> input(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", e.getMessage() == null ? "Invalid input." : e.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> size() {
    return ResponseEntity.status(413).body(Map.of("error", "Maximum upload size: 5 MB."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> duplicate() {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "error", "This action conflicts with an existing record. Refresh and try again."));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<?> missing() {
    return ResponseEntity.status(404).body(Map.of("error", "Not found."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("API request failed", e);
    return ResponseEntity.status(500)
        .body(Map.of("error", "Unable to complete the request. Please try again."));
  }
}
