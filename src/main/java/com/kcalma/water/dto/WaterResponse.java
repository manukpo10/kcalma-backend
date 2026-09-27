package com.kcalma.water.dto;

import java.time.LocalDate;

/** Body for POST /api/water: the day's new total after applying the delta, alongside the day's water target. */
public record WaterResponse(LocalDate date, int totalMl, int targetMl) {}
