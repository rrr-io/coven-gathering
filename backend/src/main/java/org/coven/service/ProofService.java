package org.coven.service;

import static org.coven.repository.CovenRepository.number;
import static org.coven.service.GatheringService.bad;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.coven.security.AccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProofService {
  private final CovenRepository db;
  private final RaceService race;
  private final Clock clock;
  private final Path directory;

  public ProofService(
      CovenRepository db, RaceService race, Clock clock, @Value("${app.proofs-dir}") String dir)
      throws Exception {
    this.db = db;
    this.race = race;
    this.clock = clock;
    this.directory = Paths.get(dir).toAbsolutePath().normalize();
    Files.createDirectories(directory);
  }

  @Transactional
  public Map<String, Object> submit(String user, String url, MultipartFile file) throws Exception {
    var today = race.reconcile();
    var p = db.one("SELECT * FROM participants WHERE user_id=? AND active=TRUE", user);
    if (p == null) throw bad("Accept your clan before checking in.");
    var s =
        RaceRules.windows(today.day(), today.config().path("schedules"), clock.millis()).stream()
            .filter(w -> w.get("status").equals("LIVE"))
            .findFirst()
            .orElseThrow(() -> bad("Proof submission opens during a live schedule."));
    if (number(p.get("created_at")) >= number(s.get("startAt")))
      throw bad("You joined after this window began. Your first check-in opens next schedule.");
    if (db.one(
            "SELECT id FROM proofs WHERE participant_id=? AND race_day_id=? AND schedule_id=?",
            p.get("id"),
            today.day().toString(),
            s.get("id"))
        != null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A proof has already been submitted for this schedule.");
    String object = null, type = null;
    byte[] bytes;
    if (file != null && !file.isEmpty()) {
      if (file.getSize() > 5 * 1024 * 1024) throw bad("Maximum upload size: 5 MB.");
      bytes = file.getBytes();
      type = imageType(bytes);
      if (type == null) throw bad("Use a valid JPG, PNG or WebP image.");
      object = UUID.randomUUID().toString();
    } else {
      url = url == null ? "" : url.trim();
      if (url.length() > 4000 || !ConfigurationService.validUrl(url))
        throw bad("Add a valid proof URL or an image.");
      bytes = url.getBytes(StandardCharsets.UTF_8);
    }
    if (clock.millis() >= number(s.get("endAt"))) throw bad("The schedule closed while uploading.");
    String hash = AccessService.hash(bytes),
        status =
            db.one("SELECT id FROM proofs WHERE hash=?", hash) == null ? "PENDING" : "DUPLICATE";
    if (object != null) {
      Path path = directory.resolve(object);
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int state) {
              if (state != STATUS_COMMITTED)
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            }
          });
    }
    db.execute(
        "INSERT INTO"
            + " proofs(id,race_day_id,schedule_id,participant_id,clan,country,region,submitted_at,proof_url,object_key,content_type,hash,status)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        today.day().toString(),
        s.get("id"),
        p.get("id"),
        p.get("clan"),
        p.get("country"),
        p.get("region"),
        clock.millis(),
        object == null ? url : null,
        object,
        type,
        hash,
        status);
    return Map.of("ok", true, "status", status);
  }

  public record Image(byte[] bytes, String type) {}

  public Image image(String id, String user, boolean admin) throws Exception {
    var p =
        db.one(
            "SELECT proofs.*,participants.user_id AS owner FROM proofs JOIN participants ON"
                + " participants.id=proofs.participant_id WHERE proofs.id=?",
            id);
    if (p == null || (!admin && !user.equals(p.get("owner"))))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof not found.");
    if (p.get("object_key") == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No image.");
    String key = (String) p.get("object_key");
    if (!key.matches("[0-9a-f-]{36}")) throw new IllegalStateException("Invalid proof key");
    Path path = directory.resolve(key).normalize();
    if (!path.startsWith(directory) || !Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof image unavailable.");
    return new Image(Files.readAllBytes(path), (String) p.get("content_type"));
  }

  private static String imageType(byte[] a) {
    if (a.length >= 3 && (a[0] & 255) == 255 && (a[1] & 255) == 216 && (a[2] & 255) == 255)
      return "image/jpeg";
    if (a.length >= 8
        && (a[0] & 255) == 137
        && a[1] == 80
        && a[2] == 78
        && a[3] == 71
        && a[4] == 13
        && a[5] == 10
        && a[6] == 26
        && a[7] == 10) return "image/png";
    if (a.length >= 12
        && new String(a, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(a, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
    return null;
  }
}
