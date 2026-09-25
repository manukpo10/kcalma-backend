package com.kcalma.food.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Body for POST /api/food/entries: batch-saves one or more confirmed items. */
public record SaveFoodEntriesRequest(@NotEmpty @Valid List<FoodEntryRequest> entries) {}
