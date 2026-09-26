package com.kcalma.food.reference;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure text-normalization helpers for {@link FoodReferenceMatcher}. No Spring, no I/O — safe to
 * unit test exhaustively.
 */
public final class NameNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern MULTI_SPACE = Pattern.compile(" +");

    private static final Set<String> COOKING_KEYWORDS = Set.of(
            "cooked", "roasted", "grilled", "fried", "baked", "boiled", "steamed", "braised", "sauteed", "sautéed",
            "seared", "poached", "stewed", "smoked", "broiled", "toasted", "simmered");

    private NameNormalizer() {}

    /**
     * Spanish-aware normalization for the personal-library lookup key (matches {@code
     * app.user_food.normalized_name}): lowercase, accents stripped, punctuation collapsed to
     * spaces, whitespace trimmed/collapsed. {@code null} normalizes to an empty string.
     */
    public static String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(rawName.toLowerCase(Locale.forLanguageTag("es")), Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        String alnumOnly = NON_ALNUM_SPACE.matcher(withoutDiacritics).replaceAll(" ");
        return MULTI_SPACE.matcher(alnumOnly).replaceAll(" ").trim();
    }

    /**
     * Lowercase, trimmed English canonical name, used as the trigram search query against {@code
     * app.food_reference.search_name} (which is built the same way — see {@code
     * tools/usda/build_food_reference.py}). {@code null} normalizes to an empty string.
     */
    public static String normalizeCanonical(String canonicalNameEn) {
        if (canonicalNameEn == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(canonicalNameEn.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        return MULTI_SPACE.matcher(withoutDiacritics.trim()).replaceAll(" ");
    }

    /**
     * True when the English canonical name itself names a cooking method — used to decide whether
     * {@link FoodReferenceMatcher} should prefer a "raw" USDA description or a cooked one.
     */
    public static boolean mentionsCookingMethod(String canonicalNameEn) {
        if (canonicalNameEn == null || canonicalNameEn.isBlank()) {
            return false;
        }
        String lower = canonicalNameEn.toLowerCase(Locale.ROOT);
        return COOKING_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
