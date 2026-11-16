package com.propfolio.documents;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.bills.*}.
 *
 * @param autoExtract              read each bill with OpenAI right after it is uploaded
 * @param readyThreshold           combined confidence (0–1) a bill needs, with no issues, to be READY
 * @param extractionStalledAfter   a reading still unfinished after this long is shown as failed
 */
@ConfigurationProperties(prefix = "app.bills")
public record BillProcessingProperties(boolean autoExtract, BigDecimal readyThreshold,
        Duration extractionStalledAfter) {

    public BillProcessingProperties {
        readyThreshold = readyThreshold == null ? new BigDecimal("0.85") : readyThreshold;
        extractionStalledAfter = extractionStalledAfter == null ? Duration.ofMinutes(10) : extractionStalledAfter;
    }
}
