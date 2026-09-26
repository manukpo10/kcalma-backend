package com.kcalma.food.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FoodReferenceMatcher}'s priority order (personal library, then USDA
 * reference, then the Gemini estimate as a last resort) and threshold behavior, against mocked
 * ("fake") repositories — the trigram SQL itself can't be exercised without a real Postgres (see
 * V4__enable_pg_trgm_and_food_reference.sql), so what's verified here is that the matcher queries
 * the right thing, at the right threshold, and maps each outcome to the right {@link FoodSource}.
 */
@ExtendWith(MockitoExtension.class)
class FoodReferenceMatcherTest {

    @Mock
    private UserFoodRepository userFoodRepository;

    @Mock
    private FoodReferenceRepository foodReferenceRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void resolve_exactPersonalMatch_winsOverEverythingElseAndNeverQueriesFoodReference() {
        UserFood userFood = userFood("milanesa de carne", "Milanesa de carne (mi receta)", UserFoodSource.USER, null);
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa de carne"))
                .thenReturn(Optional.of(userFood));

        List<ResolvedFoodItem> result = newMatcher().resolve(userId, List.of(item("Milanesa de carne", "beef, cooked")));

        assertThat(result).hasSize(1);
        ResolvedFoodItem resolved = result.get(0);
        assertThat(resolved.source()).isEqualTo(FoodSource.PERSONAL);
        assertThat(resolved.matchedDescription()).isEqualTo("Milanesa de carne (mi receta)");
        assertThat(resolved.per100().kcal()).isEqualTo(250);
        verify(userFoodRepository, never()).findBestFuzzyMatch(any(), anyString(), anyDouble());
        verify(foodReferenceRepository, never()).findBestMatch(anyString(), anyDouble(), anyBoolean());
    }

    @Test
    void resolve_noExactPersonalMatch_fallsBackToFuzzyPersonalMatchAtTheStricterThreshold() {
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa")).thenReturn(Optional.empty());
        UserFood fuzzy = userFood("milanesa de carne", "Milanesa (guardada)", UserFoodSource.USDA, 999L);
        when(userFoodRepository.findBestFuzzyMatch(userId, "milanesa", FoodReferenceMatcher.PERSONAL_SIMILARITY_THRESHOLD))
                .thenReturn(Optional.of(fuzzy));

        List<ResolvedFoodItem> result = newMatcher().resolve(userId, List.of(item("Milanesa", "beef, cooked")));

        assertThat(result.get(0).source()).isEqualTo(FoodSource.PERSONAL);
        assertThat(result.get(0).fdcId()).isEqualTo(999L);
        verify(foodReferenceRepository, never()).findBestMatch(anyString(), anyDouble(), anyBoolean());
    }

    @Test
    void resolve_noPersonalMatch_fallsBackToFoodReferenceAtTheLooserThreshold() {
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());
        FoodReference reference = foodReference(555L, "Beef, ground, 80% lean, cooked");
        // The canonical name itself says "cooked", so the matcher must ask for preferRaw=false.
        when(foodReferenceRepository.findBestMatch(
                        eq("beef, ground, 80% lean, cooked"), eq(FoodReferenceMatcher.REFERENCE_SIMILARITY_THRESHOLD), eq(false)))
                .thenReturn(Optional.of(reference));

        List<ResolvedFoodItem> result =
                newMatcher().resolve(userId, List.of(item("Carne picada", "beef, ground, 80% lean, cooked")));

        ResolvedFoodItem resolved = result.get(0);
        assertThat(resolved.source()).isEqualTo(FoodSource.USDA);
        assertThat(resolved.fdcId()).isEqualTo(555L);
        assertThat(resolved.matchedDescription()).isEqualTo("Beef, ground, 80% lean, cooked");
        assertThat(resolved.per100().kcal()).isEqualTo(272);
    }

    @Test
    void resolve_canonicalNameNamesACookingMethod_asksFoodReferenceToNotPreferRaw() {
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());
        when(foodReferenceRepository.findBestMatch(anyString(), anyDouble(), anyBoolean())).thenReturn(Optional.empty());

        newMatcher().resolve(userId, List.of(item("Pollo grillado", "chicken, breast, grilled")));

        verify(foodReferenceRepository).findBestMatch(eq("chicken, breast, grilled"), anyDouble(), eq(false));
    }

    @Test
    void resolve_canonicalNameWithNoCookingMethod_asksFoodReferenceToPreferRaw() {
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());
        when(foodReferenceRepository.findBestMatch(anyString(), anyDouble(), anyBoolean())).thenReturn(Optional.empty());

        newMatcher().resolve(userId, List.of(item("Frutillas", "strawberries, raw")));

        verify(foodReferenceRepository).findBestMatch(eq("strawberries, raw"), anyDouble(), eq(true));
    }

    @Test
    void resolve_noMatchAnywhere_fallsBackToTheGeminiEstimateWithNoFdcId() {
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());
        when(foodReferenceRepository.findBestMatch(anyString(), anyDouble(), anyBoolean())).thenReturn(Optional.empty());

        List<ResolvedFoodItem> result =
                newMatcher().resolve(userId, List.of(item("Guiso de lentejas casero", "lentil stew")));

        ResolvedFoodItem resolved = result.get(0);
        assertThat(resolved.source()).isEqualTo(FoodSource.ESTIMATED);
        assertThat(resolved.fdcId()).isNull();
        assertThat(resolved.matchedDescription()).isNull();
        assertThat(resolved.per100().kcal()).isEqualTo(120);
    }

    @Test
    void resolve_blankItemName_skipsThePersonalLibraryEntirely() {
        when(foodReferenceRepository.findBestMatch(anyString(), anyDouble(), anyBoolean())).thenReturn(Optional.empty());

        newMatcher().resolve(userId, List.of(item("   ", "unknown food")));

        verify(userFoodRepository, never()).findByUserIdAndNormalizedName(any(), any());
        verify(userFoodRepository, never()).findBestFuzzyMatch(any(), any(), anyDouble());
    }

    @Test
    void resolve_blankCanonicalName_skipsFoodReferenceEntirelyAndEstimates() {
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());

        List<ResolvedFoodItem> result = newMatcher().resolve(userId, List.of(item("Comida rara", "")));

        assertThat(result.get(0).source()).isEqualTo(FoodSource.ESTIMATED);
        verify(foodReferenceRepository, never()).findBestMatch(anyString(), anyDouble(), anyBoolean());
    }

    @Test
    void resolve_multipleItems_resolvesEachIndependently() {
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "frutillas"))
                .thenReturn(Optional.of(userFood("frutillas", "Frutillas (mías)", UserFoodSource.USER, null)));
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "arroz")).thenReturn(Optional.empty());
        when(userFoodRepository.findBestFuzzyMatch(any(), any(), anyDouble())).thenReturn(Optional.empty());
        when(foodReferenceRepository.findBestMatch(anyString(), anyDouble(), anyBoolean())).thenReturn(Optional.empty());

        List<ResolvedFoodItem> result = newMatcher()
                .resolve(userId, List.of(item("Frutillas", "strawberries, raw"), item("Arroz", "rice, white")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).source()).isEqualTo(FoodSource.PERSONAL);
        assertThat(result.get(1).source()).isEqualTo(FoodSource.ESTIMATED);
    }

    private FoodReferenceMatcher newMatcher() {
        return new FoodReferenceMatcher(userFoodRepository, foodReferenceRepository);
    }

    private static AnalyzedFoodItem item(String name, String canonicalNameEn) {
        return new AnalyzedFoodItem(name, canonicalNameEn, 150, 120, 6, 4, 15, 2, 3, 200);
    }

    private UserFood userFood(String normalizedName, String displayName, UserFoodSource source, Long fdcId) {
        return new UserFood(
                userId, normalizedName, displayName, new NutritionMath.Per100(250, 20, 18, 2, 1, 0.5, 480), source, fdcId);
    }

    private static FoodReference foodReference(long fdcId, String description) {
        return new FoodReference(
                fdcId,
                description,
                "foundation_food",
                new BigDecimal("272"),
                new BigDecimal("27"),
                new BigDecimal("18"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("90"),
                description.toLowerCase(java.util.Locale.ROOT));
    }
}
