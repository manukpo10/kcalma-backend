package com.kcalma.food;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Detects an image's real format from its magic bytes instead of trusting the client-supplied
 * Content-Type, which is trivial to spoof (any multipart request can set an arbitrary "Content-Type"
 * on a part regardless of its actual bytes). Only the formats {@link FoodController} accepts for
 * photo analysis are recognized: JPEG, PNG, WebP and HEIC/HEIF.
 */
public final class ImageFormatDetector {

    private ImageFormatDetector() {}

    /** A recognized image format, paired with the MIME type to forward to the analysis provider. */
    public enum ImageFormat {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp"),
        HEIC("image/heic");

        private final String mimeType;

        ImageFormat(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    private static final int[] JPEG_MAGIC = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG_MAGIC = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "mif1");

    /** @return the detected format, or empty if the bytes don't match any supported signature. */
    public static Optional<ImageFormat> detect(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return Optional.of(ImageFormat.JPEG);
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Optional.of(ImageFormat.PNG);
        }
        if (isWebp(bytes)) {
            return Optional.of(ImageFormat.WEBP);
        }
        if (isHeic(bytes)) {
            return Optional.of(ImageFormat.HEIC);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((bytes[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** RIFF container: "RIFF" at offset 0, 4-byte chunk size, "WEBP" fourCC at offset 8. */
    private static boolean isWebp(byte[] bytes) {
        return matchesAscii(bytes, 0, "RIFF") && matchesAscii(bytes, 8, "WEBP");
    }

    /** ISOBMFF 'ftyp' box: 4-byte box size, "ftyp" at offset 4, major brand at offset 8. */
    private static boolean isHeic(byte[] bytes) {
        if (bytes.length < 12 || !matchesAscii(bytes, 4, "ftyp")) {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return HEIC_BRANDS.contains(brand);
    }

    private static boolean matchesAscii(byte[] bytes, int offset, String ascii) {
        if (bytes.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (bytes[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
