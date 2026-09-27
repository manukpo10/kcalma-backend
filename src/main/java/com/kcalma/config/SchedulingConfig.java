package com.kcalma.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} processing for the whole app — {@code
 * com.kcalma.reminder.ReminderScheduler}'s once-a-minute tick, and {@code
 * com.kcalma.ratelimit.GeminiRateLimiter}'s once-an-hour inactive-user eviction. Kept as its own
 * tiny config class (rather than annotating {@code KcalmaApiApplication}) so a {@code @WebMvcTest}
 * slice never accidentally starts either scheduled job: slice tests don't scan {@code
 * com.kcalma.config}'s individual {@code @Configuration} classes unless explicitly {@code
 * @Import}ed, same reasoning as {@link ClockConfig}/{@link OpenApiConfig} already being separate.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
