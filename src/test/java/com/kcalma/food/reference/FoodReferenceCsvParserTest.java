package com.kcalma.food.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FoodReferenceCsvParser}: the fixed-column mapping onto {@link
 * FoodReferenceCsvRow} and the quoted-field handling that a plain {@code String.split(",")} would
 * get wrong on real USDA descriptions (which routinely contain commas).
 */
class FoodReferenceCsvParserTest {

    @Test
    void parseLine_mapsColumnsInOrderOntoTheRightFields() {
        String line = "167512,\"Pillsbury Golden Layer Buttermilk Biscuits\",sr_legacy_food,"
                + "307,5.88,13.24,41.18,1.2,5.88,1059,\"pillsbury golden layer buttermilk biscuits\"";

        FoodReferenceCsvRow row = FoodReferenceCsvParser.parseLine(line);

        assertThat(row.fdcId()).isEqualTo(167512L);
        assertThat(row.description()).isEqualTo("Pillsbury Golden Layer Buttermilk Biscuits");
        assertThat(row.dataType()).isEqualTo("sr_legacy_food");
        assertThat(row.kcalPer100()).isEqualByComparingTo(new BigDecimal("307"));
        assertThat(row.proteinPer100()).isEqualByComparingTo(new BigDecimal("5.88"));
        assertThat(row.fatPer100()).isEqualByComparingTo(new BigDecimal("13.24"));
        assertThat(row.carbsPer100()).isEqualByComparingTo(new BigDecimal("41.18"));
        assertThat(row.fiberPer100()).isEqualByComparingTo(new BigDecimal("1.2"));
        assertThat(row.sugarPer100()).isEqualByComparingTo(new BigDecimal("5.88"));
        assertThat(row.sodiumMgPer100()).isEqualByComparingTo(new BigDecimal("1059"));
        assertThat(row.searchName()).isEqualTo("pillsbury golden layer buttermilk biscuits");
    }

    @Test
    void parseLine_quotedFieldWithEmbeddedCommas_isKeptAsOneField() {
        String line = "167514,\"Kraft Foods, Shake N Bake Original Recipe, Coating for Pork, dry\","
                + "sr_legacy_food,377,6.1,3.7,79.8,0,0,2182,"
                + "\"kraft foods, shake n bake original recipe, coating for pork, dry\"";

        FoodReferenceCsvRow row = FoodReferenceCsvParser.parseLine(line);

        assertThat(row.description()).isEqualTo("Kraft Foods, Shake N Bake Original Recipe, Coating for Pork, dry");
        assertThat(row.searchName()).isEqualTo("kraft foods, shake n bake original recipe, coating for pork, dry");
    }

    @Test
    void parseLine_doubledQuotesInsideAQuotedField_unescapeToASingleQuote() {
        String line = "1,\"Ben & Jerry\"\"s Ice Cream\",sr_legacy_food,200,3,10,20,0,15,50,\"ben & jerry's ice cream\"";

        FoodReferenceCsvRow row = FoodReferenceCsvParser.parseLine(line);

        assertThat(row.description()).isEqualTo("Ben & Jerry\"s Ice Cream");
    }

    @Test
    void parseLine_wrongColumnCount_throws() {
        assertThatThrownBy(() -> FoodReferenceCsvParser.parseLine("1,Too Few Columns,sr_legacy_food"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11");
    }
}
