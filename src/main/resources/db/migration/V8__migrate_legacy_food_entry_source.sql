-- V7 repurposed app.food_entry.source from "how the entry was created" {PHOTO, MANUAL} to
-- "where its nutrient values came from" {PERSONAL, USDA, ESTIMATED, MANUAL}, but left the rows
-- logged before it untouched. Those legacy PHOTO rows cannot be read by the FoodSource enum and
-- made /api/day and /api/food/entries fail with 500. Their nutrient values were Gemini estimates,
-- so they become ESTIMATED; MANUAL keeps its meaning.
UPDATE app.food_entry
SET source = 'ESTIMATED'
WHERE source NOT IN ('PERSONAL', 'USDA', 'ESTIMATED', 'MANUAL');

-- Guard against the enum and the data drifting apart again.
ALTER TABLE app.food_entry
    ADD CONSTRAINT food_entry_source_check
    CHECK (source IN ('PERSONAL', 'USDA', 'ESTIMATED', 'MANUAL'));
