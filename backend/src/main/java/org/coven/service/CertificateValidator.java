package org.coven.service;

import static org.coven.service.ParticipationService.bad;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.coven.security.AccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Recognizes certificate structure, not issuer authenticity or screenshot provenance. */
@Service
public class CertificateValidator {
  private final String command;
  private final Semaphore capacity = new Semaphore(1);
  private static final Pattern NUMBER =
      Pattern.compile("(?im)^\\s*NO\\s*[.:]\\s*([A-Z0-9]{8,40})\\s*$");
  private static final Pattern DATE =
      Pattern.compile("\\b(20\\d{2})[.\\-/](\\d{2})[.\\-/](\\d{2})\\s+(\\d{2}):(\\d{2})\\b");

  public CertificateValidator(@Value("${app.ocr-command:tesseract}") String command) {
    this.command = command;
  }

  public String validate(byte[] bytes) throws Exception {
    if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
      throw bad("Upload a certificate up to 5 MB.");
    // Inspect dimensions before decoding to bound OCR memory usage.
    try (var input =
        new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw bad("Upload the original certificate as a PNG or JPG.");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (!(format.equals("png") || format.equals("jpeg"))
            || width < 300
            || height < 400
            || width > 4096
            || height > 6000
            || (long) width * height > 16_000_000)
          throw bad(
              "Use a readable PNG or JPG certificate, up to 4096 × 6000 pixels and 16 megapixels.");
      } finally {
        reader.dispose();
      }
    } catch (java.io.IOException e) {
      throw bad("The image is damaged or unreadable. Upload the original PNG or JPG certificate.");
    }
    if (!capacity.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Certificate scanner is busy. Please retry shortly.");
    Process process = null;
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var builder = new ProcessBuilder(command, "stdin", "stdout", "-l", "eng", "--psm", "11");
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      builder.environment().put("OMP_THREAD_LIMIT", "1");
      process = builder.start();
      final Process running = process;
      var write =
          workers.submit(
              () -> {
                try (var out = running.getOutputStream()) {
                  out.write(bytes);
                }
                return true;
              });
      var read = workers.submit(() -> running.getInputStream().readNBytes(32_769));
      if (!running.waitFor(20, TimeUnit.SECONDS)) {
        running.destroyForcibly();
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Certificate scan timed out. Please retry.");
      }
      write.get(2, TimeUnit.SECONDS);
      byte[] output = read.get(2, TimeUnit.SECONDS);
      if (running.exitValue() != 0 || output.length > 32_768)
        throw bad("Unable to read this certificate. Upload the original PNG or JPG.");
      return fingerprint(new String(output, StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Certificate scanner is unavailable.");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      capacity.release();
    }
  }

  static String fingerprint(String text) {
    String upper = text.toUpperCase(Locale.ROOT);
    String compact = upper.replaceAll("\\s+", " ");
    var number = NUMBER.matcher(upper);
    var date = DATE.matcher(upper);
    if (!compact.contains("VOTING CERTIFICATE")
        || !compact.contains("PARTICIPATION DATE")
        || !compact.contains("ISSUED BY MNET PLUS")
        || !number.find()
        || !date.find())
      throw bad(
          "Certificate not recognized. Upload the full Mnet Plus voting certificate with its date"
              + " and number, not a voting-screen screenshot.");
    try {
      LocalDateTime.of(
          Integer.parseInt(date.group(1)),
          Integer.parseInt(date.group(2)),
          Integer.parseInt(date.group(3)),
          Integer.parseInt(date.group(4)),
          Integer.parseInt(date.group(5)));
    } catch (RuntimeException e) {
      throw bad("The certificate date could not be read.");
    }
    // Normalize common OCR ambiguities. Never store the readable certificate identifier.
    String id = number.group(1).replace('O', '0').replace('I', '1');
    return AccessService.hash(("mnet-plus:" + id).getBytes(StandardCharsets.UTF_8));
  }
}
