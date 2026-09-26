#!/usr/bin/env python3
"""Builds src/main/resources/usda/food_reference.csv.gz from USDA FoodData Central bulk CSVs.

Regenerating this file
-----------------------
1. Download the dataset zips (check https://fdc.nal.usda.gov/download-datasets for the current
   filenames/dates -- SR Legacy is a frozen, no-longer-updated release, so its filename never
   changes). Note: `www.usda.gov` mirrors the same paths but blocks a plain `curl` with a 403 bot
   check -- `fdc.nal.usda.gov` serves the exact same files without that check.

     curl -o sr_legacy.zip  "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
     curl -o foundation.zip "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_csv_<date>.zip"
     curl -o survey.zip     "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_<date>.zip"

2. Run this script (stdlib only, Python 3.9+) against the three zips directly -- there is no need
   to unzip them first, every CSV is read straight out of the archive:

     python3 tools/usda/build_food_reference.py \\
         --sr-legacy sr_legacy.zip --foundation foundation.zip --survey survey.zip \\
         --out src/main/resources/usda/food_reference.csv.gz

   `--survey` is optional: drop it to ship only SR Legacy + Foundation Foods if the FNDDS mixed
   dishes ever push the gzipped output past the ~1.5 MB budget.

Output format
-------------
Gzipped CSV, NO header row, one row per food, columns in this fixed order (must match
com.kcalma.food.reference.FoodReferenceCsvParser on the Java side):

    fdc_id, description, data_type, kcal_per_100, protein_per_100, fat_per_100,
    carbs_per_100, fiber_per_100, sugar_per_100, sodium_mg_per_100, search_name

Rows are sorted by fdc_id for a stable, reviewable diff on regeneration.

Nutrient-id mapping -- the gotcha this script exists to get right
-------------------------------------------------------------------
SR Legacy and Foundation Foods key food_nutrient.csv's "nutrient_id" column by nutrient.csv's own
internal "id" column (e.g. id 1008 = "Energy" in kcal, id 1003 = "Protein"). The FNDDS/Survey
dataset instead reuses the CLASSIC USDA nutrient NUMBER for that same "nutrient_id" column (e.g.
208 = Energy/kcal, 203 = Protein) -- both datasets' own nutrient.csv were inspected to confirm
this, and it was cross-checked against a known food (fdc_id 2705385, "Milk, whole": mapping below
yields 61 kcal, 3.27 g protein, 3.2 g fat, 4.63 g carbs, 4.81 g sugar, 38 mg sodium per 100 g --
all correct).

Foundation Foods also reports energy under different ids for most of its foods: as of the
2026-04-30 release, only ~29% of "foundation_food" rows carry the plain "Energy" id (1008); most
instead carry only "Energy (Atwater Specific Factors)" (2048) and/or "...(Atwater General
Factors)" (2047), in that preference order (specific-to-this-food beats a general formula). A food
with none of the three present has no reliable kcal value and is dropped, per the fallback chains
below.

Only rows whose food.csv "data_type" is the dataset's own canonical type are kept -- Foundation
Foods' food.csv is mostly lab-workflow rows (sample_food/market_acquisition/sub_sample_food/
agricultural_acquisition), not the curated "foundation_food" aggregate a consumer app should show.
"""
import argparse
import csv
import gzip
import io
import sys
import unicodedata
import zipfile
from collections import defaultdict

# SR Legacy & Foundation Foods: food_nutrient.nutrient_id == nutrient.csv's own "id" column.
ID_SCHEME = {
    "kcal": ["1008", "2048", "2047"],  # Energy, else Atwater Specific Factors, else Atwater General
    "protein": ["1003"],
    "fat": ["1004"],
    "carbs": ["1005", "1050"],  # Carbohydrate, by difference, else by summation
    "fiber": ["1079"],
    "sugar": ["2000", "1063"],  # Sugars, Total, else the foundation/NLEA naming of the same slot
    "sodium": ["1093"],
}

# FNDDS/Survey: food_nutrient.nutrient_id is instead the classic USDA nutrient NUMBER, a totally
# different id space from the one above (see module docstring).
NUTRIENT_NBR_SCHEME = {
    "kcal": ["208"],
    "protein": ["203"],
    "fat": ["204"],
    "carbs": ["205"],
    "fiber": ["291"],
    "sugar": ["269"],
    "sodium": ["307"],
}

FIELDS = ["kcal", "protein", "fat", "carbs", "fiber", "sugar", "sodium"]


def read_csv_member(zip_path, member_name):
    """Yields csv.reader rows for one member file inside a zip, matched by exact basename
    (the real file sits inside a dated top-level folder, e.g. ".../food.csv")."""
    with zipfile.ZipFile(zip_path) as zf:
        names = [n for n in zf.namelist() if n.rsplit("/", 1)[-1] == member_name]
        if len(names) != 1:
            raise SystemExit(f"expected exactly one {member_name!r} in {zip_path}, found {names}")
        with zf.open(names[0]) as fh:
            text = io.TextIOWrapper(fh, encoding="utf-8", newline="")
            yield from csv.reader(text)


def strip_accents(text):
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def build_search_name(description):
    return " ".join(strip_accents(description.lower()).split())


def extract_dataset(zip_path, wanted_data_type, scheme):
    """Returns {fdc_id: {"description", "data_type", **FIELDS}} for one dataset zip, energy
    fallback chain applied, foods with no energy value dropped entirely."""
    food_rows = list(read_csv_member(zip_path, "food.csv"))
    header = food_rows[0]
    fdc_idx = header.index("fdc_id")
    dt_idx = header.index("data_type")
    desc_idx = header.index("description")

    foods = {}
    for row in food_rows[1:]:
        if row[dt_idx] != wanted_data_type:
            continue
        foods[row[fdc_idx]] = {"description": row[desc_idx], "data_type": row[dt_idx]}

    wanted_ids = {nid for ids in scheme.values() for nid in ids}
    amounts = defaultdict(dict)

    nutrient_rows = read_csv_member(zip_path, "food_nutrient.csv")
    nheader = next(nutrient_rows)
    nfdc_idx = nheader.index("fdc_id")
    nid_idx = nheader.index("nutrient_id")
    amt_idx = nheader.index("amount")
    for row in nutrient_rows:
        fdc_id = row[nfdc_idx]
        if fdc_id not in foods:
            continue
        nid = row[nid_idx]
        if nid not in wanted_ids or row[amt_idx] == "":
            continue
        amounts[fdc_id].setdefault(nid, row[amt_idx])

    results = {}
    dropped_no_energy = 0
    for fdc_id, food in foods.items():
        food_amounts = amounts.get(fdc_id, {})
        values = {}
        for field in FIELDS:
            value = None
            for nid in scheme[field]:
                if nid in food_amounts:
                    value = food_amounts[nid]
                    break
            values[field] = value
        if values["kcal"] is None:
            dropped_no_energy += 1
            continue
        for field in FIELDS:
            if values[field] is None:
                values[field] = "0"
        results[fdc_id] = {**food, **values}

    print(
        f"{zip_path} [{wanted_data_type}]: kept {len(results)}, dropped {dropped_no_energy} (no energy value)",
        file=sys.stderr,
    )
    return results


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--sr-legacy", required=True, help="path to the SR Legacy CSV zip")
    parser.add_argument("--foundation", required=True, help="path to the Foundation Foods CSV zip")
    parser.add_argument("--survey", help="path to the FNDDS/Survey Foods CSV zip (optional)")
    parser.add_argument("--out", required=True, help="output path, e.g. src/main/resources/usda/food_reference.csv.gz")
    args = parser.parse_args()

    all_foods = {}
    all_foods.update(extract_dataset(args.sr_legacy, "sr_legacy_food", ID_SCHEME))
    all_foods.update(extract_dataset(args.foundation, "foundation_food", ID_SCHEME))
    if args.survey:
        all_foods.update(extract_dataset(args.survey, "survey_fndds_food", NUTRIENT_NBR_SCHEME))

    rows = []
    for fdc_id, food in all_foods.items():
        rows.append(
            [
                fdc_id,
                food["description"],
                food["data_type"],
                food["kcal"],
                food["protein"],
                food["fat"],
                food["carbs"],
                food["fiber"],
                food["sugar"],
                food["sodium"],
                build_search_name(food["description"]),
            ]
        )
    rows.sort(key=lambda r: int(r[0]))

    with gzip.open(args.out, "wt", encoding="utf-8", newline="") as f:
        csv.writer(f).writerows(rows)

    print(f"wrote {len(rows)} foods to {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
