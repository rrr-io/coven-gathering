package org.coven.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.*;
import java.util.Map;
import org.coven.security.AccessService;
import org.coven.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class CovenController {
  private final AccessService access;
  private final RaceService race;
  private final GatheringService gathering;
  private final ProofService proofs;

  public CovenController(
      AccessService access, RaceService race, GatheringService gathering, ProofService proofs) {
    this.access = access;
    this.race = race;
    this.gathering = gathering;
    this.proofs = proofs;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/coven")
  public Object state(HttpServletRequest req, HttpServletResponse res) {
    return race.state(access.session(req, res), access.isAdmin(req));
  }

  @PostMapping(value = "/coven", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Object action(@RequestBody JsonNode b, HttpServletRequest req, HttpServletResponse res) {
    return gathering.action(access.session(req, res), access.isAdmin(req), access.adminName(), b);
  }

  @PostMapping(value = "/coven", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object proof(
      @RequestParam(required = false) String url,
      @RequestParam(required = false) MultipartFile file,
      HttpServletRequest req,
      HttpServletResponse res)
      throws Exception {
    return proofs.submit(access.session(req, res), url, file);
  }

  @GetMapping("/proof/{id}")
  public ResponseEntity<byte[]> image(
      @PathVariable String id, HttpServletRequest req, HttpServletResponse res) throws Exception {
    var image = proofs.image(id, access.session(req, res), access.isAdmin(req));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.type()))
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }

  @PostMapping("/admin/tick")
  public Object tick(HttpServletRequest req) {
    access.requireAdmin(req);
    return Map.of("ok", true, "race_day_id", race.reconcile().day().toString());
  }
}
