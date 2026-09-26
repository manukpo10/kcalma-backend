-- Dishes are now decomposed into ingredients (see com.kcalma.food.analysis.AnalyzedDish), and a
-- dish's own source can be an aggregate of its ingredients' sources (com.kcalma.food.FoodSource#combine)
-- — including a brand new 'MIXED' value the V8 CHECK constraint doesn't allow yet. Drop + re-add in
-- this same migration so the constraint and the enum never drift apart, same as V8 did for the
-- original four values.
ALTER TABLE app.food_entry DROP CONSTRAINT food_entry_source_check;

ALTER TABLE app.food_entry
    ADD CONSTRAINT food_entry_source_check
    CHECK (source IN ('PERSONAL', 'USDA', 'ESTIMATED', 'MANUAL', 'MIXED'));

-- Nullable, no backfill: every row logged before this migration has no breakdown to show, and
-- must keep reading/updating exactly as it did before (see com.kcalma.food.FoodEntry#ingredients).
ALTER TABLE app.food_entry ADD COLUMN ingredients jsonb;
