package com.kcalma.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of "now" for the whole domain, zoned to {@code app.timezone} (env var {@code
 * APP_TIMEZONE}, default {@code America/Argentina/Buenos_Aires} — see application.yml) instead of
 * the JVM/container's default zone (production runs on Render, which is UTC). Every "today"/now
 * computation that matters to the user (streaks, future-date checks, age) must go through {@code
 * LocalDate.now(clock)}/{@code LocalDateTime.now(clock)} — never the zone-less {@code .now()} —
 * so a user logging something late at night in Argentina always gets Argentina's calendar day,
 * not a day that already rolled over in UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
