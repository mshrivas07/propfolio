package com.propfolio.extraction;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.openai.*}. The key comes from the OPENAI_API_KEY environment variable
 * (backend/.env) and is never logged.
 *
 * @param apiKey  OpenAI API key; when blank, readings fail with a "not set up" message
 * @param baseUrl API base URL (overridable for tests)
 * @param model   model id; any current vision-capable model works
 * @param timeout how long to wait for one reading
 */
@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model, Duration timeout) {

    public OpenAiProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        model = model == null || model.isBlank() ? "gpt-6-luna" : model;
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Keeps the key out of logs and error messages if this record is ever printed. */
    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=%s, model=%s, timeout=%s, apiKey=%s]"
                .formatted(baseUrl, model, timeout, configured() ? "***" : "(not set)");
    }
}
