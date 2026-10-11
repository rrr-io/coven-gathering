package org.coven.service;

import static org.coven.repository.CovenRepository.number;
import static org.coven.service.ParticipationService.bad;

import java.time.Clock;
import java.util.*;
import org.coven.repository.CovenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProofService {
  private final CovenRepository db;
  private final GatheringService gathering;
  private final Clock clock;
  private final CertificateValidator certificates;
  private final TransactionTemplate transaction;

  public ProofService(
      CovenRepository db,
      GatheringService gathering,
      Clock clock,
      CertificateValidator certificates,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.db = db;
    this.gathering = gathering;
    this.clock = clock;
    this.certificates = certificates;
    this.transaction = new TransactionTemplate(manager);
  }

  private record Slot(Map<String, Object> participant, String day, Map<String, Object> window) {}

  private Slot slot(String user) {
    var today = gathering.reconcile();
    var p = db.one("SELECT * FROM participants WHERE user_id=? AND active=TRUE", user);
    if (p == null) throw bad("Accept your clan before checking in.");
    var s =
        GatheringRules.windows(today.day(), today.config().path("schedules"), clock.millis())
            .stream()
            .filter(w -> w.get("status").equals("LIVE"))
            .findFirst()
            .orElseThrow(() -> bad("Certificate submission opens during a live schedule."));
    if (number(p.get("created_at")) >= number(s.get("startAt")))
      throw bad("You joined after this window began. Your first check-in opens next schedule.");
    if (db.one(
            "SELECT id FROM proofs WHERE participant_id=? AND gathering_day_id=? AND schedule_id=?"
                + " AND status='VERIFIED'",
            p.get("id"),
            today.day().toString(),
            s.get("id"))
        != null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "You have already checked in for this schedule.");
    return new Slot(p, today.day().toString(), s);
  }

  public Map<String, Object> submit(String user, String url, MultipartFile file) throws Exception {
    Slot initial = transaction.execute(status -> slot(user));
    if (url != null && !url.isBlank())
      throw bad("Upload the certificate file. Proof URLs are no longer accepted.");
    if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw bad("Upload a voting certificate as a PNG or JPG, up to 5 MB.");
    // OCR runs outside the database lock. Bytes exist only for this request.
    String fingerprint = certificates.validate(file.getBytes());
    return transaction.execute(
        tx -> {
          Slot current = slot(user);
          if (!current.day().equals(initial.day())
              || !current.window().get("id").equals(initial.window().get("id")))
            throw bad("The schedule closed while scanning. Please submit during the next window.");
          if (db.one("SELECT id FROM proofs WHERE verified_hash=?", fingerprint) != null)
            return Map.of("ok", false, "status", "DUPLICATE");
          var p = current.participant();
          // A rejected legacy upload must not prevent a valid certificate retry.
          db.execute(
              "DELETE FROM proofs WHERE participant_id=? AND gathering_day_id=? AND schedule_id=?"
                  + " AND status<>'VERIFIED'",
              p.get("id"),
              current.day(),
              current.window().get("id"));
          db.execute(
              "INSERT INTO"
                  + " proofs(id,gathering_day_id,schedule_id,participant_id,clan,country,region,submitted_at,hash,verified_hash,status,verified_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
              UUID.randomUUID().toString(),
              current.day(),
              current.window().get("id"),
              p.get("id"),
              p.get("clan"),
              p.get("country"),
              p.get("region"),
              clock.millis(),
              fingerprint,
              fingerprint,
              "VERIFIED",
              clock.millis());
          return Map.of("ok", true, "status", "VERIFIED");
        });
  }
}
