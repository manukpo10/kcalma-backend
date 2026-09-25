package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.food.ImageFormatDetector.ImageFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ImageFormatDetector} — sniffing the real format from magic bytes,
 * never from a client-supplied Content-Type (which {@link FoodController} used to trust and is
 * trivial to spoof).
 */
class ImageFormatDetectorTest {

    @Test
    void detect_jpegMagicBytes_returnsJpeg() {
        byte[] bytes = withHeader(0xFF, 0xD8, 0xFF, 0xE0);

        assertThat(ImageFormatDetector.detect(bytes)).contains(ImageFormat.JPEG);
    }

    @Test
    void detect_pngMagicBytes_returnsPng() {
        byte[] bytes = withHeader(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

        assertThat(ImageFormatDetector.detect(bytes)).contains(ImageFormat.PNG);
    }

    @Test
    void detect_webpRiffContainer_returnsWebp() {
        byte[] bytes = riffContainer("WEBP");

        assertThat(ImageFormatDetector.detect(bytes)).contains(ImageFormat.WEBP);
    }

    @Test
    void detect_riffContainerThatIsNotWebp_returnsEmpty() {
        // A WAV file also starts with "RIFF...." but its fourCC is WAVE, not WEBP.
        byte[] bytes = riffContainer("WAVE");

        assertThat(ImageFormatDetector.detect(bytes)).isEmpty();
    }

    @Test
    void detect_heicFtypBrand_returnsHeic() {
        assertThat(ImageFormatDetector.detect(ftypBox("heic"))).contains(ImageFormat.HEIC);
    }

    @Test
    void detect_heixFtypBrand_returnsHeic() {
        assertThat(ImageFormatDetector.detect(ftypBox("heix"))).contains(ImageFormat.HEIC);
    }

    @Test
    void detect_hevcFtypBrand_returnsHeic() {
        assertThat(ImageFormatDetector.detect(ftypBox("hevc"))).contains(ImageFormat.HEIC);
    }

    @Test
    void detect_mif1FtypBrand_returnsHeic() {
        assertThat(ImageFormatDetector.detect(ftypBox("mif1"))).contains(ImageFormat.HEIC);
    }

    @Test
    void detect_ftypWithUnrelatedBrand_returnsEmpty() {
        // e.g. a plain MP4 ("isom") — not a photo format we accept for analysis.
        byte[] bytes = ftypBox("isom");

        assertThat(ImageFormatDetector.detect(bytes)).isEmpty();
    }

    @Test
    void detect_pdfBytesWithSpoofedImageExtension_returnsEmpty() {
        byte[] bytes = "%PDF-1.4\n%some pdf bytes".getBytes(StandardCharsets.US_ASCII);

        assertThat(ImageFormatDetector.detect(bytes)).isEmpty();
    }

    @Test
    void detect_plainTextBytes_returnsEmpty() {
        byte[] bytes = "definitely not an image".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageFormatDetector.detect(bytes)).isEmpty();
    }

    @Test
    void detect_emptyBytes_returnsEmpty() {
        assertThat(ImageFormatDetector.detect(new byte[0])).isEmpty();
    }

    @Test
    void detect_tooShortToContainAnyMagic_returnsEmptyWithoutThrowing() {
        assertThat(ImageFormatDetector.detect(new byte[] {1, 2})).isEmpty();
    }

    @Test
    void detect_nullBytes_returnsEmpty() {
        assertThat(ImageFormatDetector.detect(null)).isEmpty();
    }

    @Test
    void imageFormat_mimeType_matchesExpectedContentType() {
        assertThat(ImageFormat.JPEG.mimeType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.PNG.mimeType()).isEqualTo("image/png");
        assertThat(ImageFormat.WEBP.mimeType()).isEqualTo("image/webp");
        assertThat(ImageFormat.HEIC.mimeType()).isEqualTo("image/heic");
    }

    private static byte[] withHeader(int... headerBytes) {
        byte[] bytes = new byte[Math.max(headerBytes.length, 16)];
        for (int i = 0; i < headerBytes.length; i++) {
            bytes[i] = (byte) headerBytes[i];
        }
        return bytes;
    }

    /** "RIFF" + 4-byte size (irrelevant here) + fourCC, padded to a realistic chunk length. */
    private static byte[] riffContainer(String fourCc) {
        byte[] bytes = new byte[16];
        writeAscii(bytes, 0, "RIFF");
        writeAscii(bytes, 8, fourCc);
        return bytes;
    }

    /** 4-byte box size (irrelevant here) + "ftyp" + major brand, padded to a realistic box length. */
    private static byte[] ftypBox(String brand) {
        byte[] bytes = new byte[16];
        writeAscii(bytes, 4, "ftyp");
        writeAscii(bytes, 8, brand);
        return bytes;
    }

    private static void writeAscii(byte[] bytes, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            bytes[offset + i] = (byte) ascii.charAt(i);
        }
    }
}
