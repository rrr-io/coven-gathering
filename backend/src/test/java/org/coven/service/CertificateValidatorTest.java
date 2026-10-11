package org.coven.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CertificateValidatorTest {
  private static final String TEXT =
      "VOTING CERTIFICATE\n"
          + "Participation Date\n"
          + "2026.09.10 11:18\n"
          + "This Certificate verifies that it was issued by Mnet Plus.\n"
          + "No. 60910BVP377FZ1B0";

  @Test
  void rejectsScreenshotsWithoutCertificateFields() {
    assertThrows(
        ResponseStatusException.class,
        () -> CertificateValidator.fingerprint("Vote completed! Thank you ENHYPEN"));
    assertThrows(
        ResponseStatusException.class,
        () -> CertificateValidator.fingerprint(TEXT.replace("No. 60910BVP377FZ1B0", "")));
    assertThrows(
        ResponseStatusException.class,
        () -> CertificateValidator.fingerprint(TEXT.replace("2026.09.10", "2026.99.10")));
  }

  @Test
  void deduplicatesByCertificateNumberNotImageBytes() {
    assertEquals(
        CertificateValidator.fingerprint(TEXT),
        CertificateValidator.fingerprint(TEXT.replace("B0", "BO").toLowerCase()));
    assertNotEquals(
        CertificateValidator.fingerprint(TEXT),
        CertificateValidator.fingerprint(TEXT.replace("377", "378")));
  }

  @Test
  void scansAnImageWithoutWritingItToDisk() throws Exception {
    var image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 1200, 900);
    g.setColor(Color.BLACK);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
    String[] lines = {
      "VOTING CERTIFICATE",
      "Participation Date",
      "2026.09.10 11:18",
      "This Certificate verifies that it was",
      "issued by Mnet Plus.",
      "No. 60910BVP377FZ1B0"
    };
    for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 60, 90 + i * 115);
    g.dispose();
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    assertEquals(
        CertificateValidator.fingerprint(TEXT),
        new CertificateValidator("tesseract").validate(bytes.toByteArray()));
    assertThrows(
        ResponseStatusException.class,
        () -> new CertificateValidator("tesseract").validate(new byte[] {1, 2, 3}));
  }
}
