package com.propfolio.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for "today" across the app, so tests can fix the date.
 * The zone decides when a lease turns current/ended; set APP_TIME_ZONE to override.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${app.time-zone:America/Toronto}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
