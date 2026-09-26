package com.kcalma.food.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameNormalizerTest {

    @Test
    void normalize_lowercasesAndStripsAccents() {
        assertThat(NameNormalizer.normalize("Milanesa de Carne")).isEqualTo("milanesa de carne");
        assertThat(NameNormalizer.normalize("puré de papas")).isEqualTo("pure de papas");
        assertThat(NameNormalizer.normalize("Ñoquis")).isEqualTo("noquis");
    }

    @Test
    void normalize_collapsesPunctuationAndWhitespace() {
        assertThat(NameNormalizer.normalize("  Empanada,   de carne!! ")).isEqualTo("empanada de carne");
        assertThat(NameNormalizer.normalize("2 empanadas (fritas)")).isEqualTo("2 empanadas fritas");
    }

    @Test
    void normalize_null_returnsEmptyString() {
        assertThat(NameNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void normalize_differentInputsThatShouldCollide_produceTheSameKey() {
        assertThat(NameNormalizer.normalize("Milanesa de carne")).isEqualTo(NameNormalizer.normalize("milanesa   de   CARNE"));
        assertThat(NameNormalizer.normalize("Milanesa de carne.")).isEqualTo(NameNormalizer.normalize("¡Milanesa de carne!"));
    }

    @Test
    void normalizeCanonical_lowercasesTrimsAndStripsAccents() {
        assertThat(NameNormalizer.normalizeCanonical("  Strawberries, Raw  ")).isEqualTo("strawberries, raw");
        assertThat(NameNormalizer.normalizeCanonical("Jalapeño, raw")).isEqualTo("jalapeno, raw");
    }

    @Test
    void normalizeCanonical_null_returnsEmptyString() {
        assertThat(NameNormalizer.normalizeCanonical(null)).isEmpty();
    }

    @Test
    void mentionsCookingMethod_detectsKnownCookingWordsCaseInsensitively() {
        assertThat(NameNormalizer.mentionsCookingMethod("Beef, ground, 80% lean, cooked")).isTrue();
        assertThat(NameNormalizer.mentionsCookingMethod("Chicken, breast, GRILLED")).isTrue();
        assertThat(NameNormalizer.mentionsCookingMethod("Potatoes, roasted")).isTrue();
    }

    @Test
    void mentionsCookingMethod_rawOrPlainDescription_returnsFalse() {
        assertThat(NameNormalizer.mentionsCookingMethod("Strawberries, raw")).isFalse();
        assertThat(NameNormalizer.mentionsCookingMethod("Milk, whole")).isFalse();
    }

    @Test
    void mentionsCookingMethod_nullOrBlank_returnsFalse() {
        assertThat(NameNormalizer.mentionsCookingMethod(null)).isFalse();
        assertThat(NameNormalizer.mentionsCookingMethod("   ")).isFalse();
    }
}
