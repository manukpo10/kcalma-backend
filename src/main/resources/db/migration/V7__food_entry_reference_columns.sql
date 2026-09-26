-- app.food_entry.source is being repurposed in application code from {PHOTO, MANUAL} (how the
-- entry was created) to {PERSONAL, USDA, ESTIMATED, MANUAL} (where its nutrient values came from)
-- — see com.kcalma.food.FoodSource. No migration needed for that part: the column was always an
-- unconstrained VARCHAR(10) (see V2__food_entry.sql, no CHECK constraint), and every new value
-- still fits within 10 characters.
ALTER TABLE app.food_entry ADD COLUMN fdc_id BIGINT REFERENCES app.food_reference (fdc_id);
