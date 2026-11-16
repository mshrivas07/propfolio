package com.propfolio.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.propfolio.documents.ExtractedBill;

/**
 * Sends one bill (PDF or image) to the OpenAI Responses API and gets back structured JSON
 * matching resources/extraction/utility-bill-schema.json (strict structured outputs).
 *
 * <p>Requests are sent with {@code store: false} so OpenAI doesn't keep the response.
 * The instructions live in resources/extraction/instructions.md and can be tuned without code changes.
 */
public class OpenAiBillReader {

    /** The model's answer: parsed, plus the raw JSON text to store in bill.extraction_json. */
    public record Reading(ExtractedBill bill, String rawJson) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final String instructions;
    private final Map<String, Object> schema;

    public OpenAiBillReader(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.instructions = resource("extraction/instructions.md");
        this.schema = parseObject(resource("extraction/utility-bill-schema.json"));
    }

    public Reading read(byte[] content, String contentType, String filename) {
        if (!properties.configured()) {
            throw new ExtractionException(
                    "Bill reading isn't set up yet. Add OPENAI_API_KEY to backend/.env and restart the backend.");
        }
        String responseBody = send(requestBody(content, contentType, filename));
        String outputText = outputText(responseBody);
        try {
            return new Reading(ExtractedBill.fromJson(outputText), outputText);
        } catch (IllegalArgumentException e) {
            throw new ExtractionException("The answer from OpenAI couldn't be understood. Try again.");
        }
    }

    String requestBody(byte[] content, String contentType, String filename) {
        String base64 = Base64.getEncoder().encodeToString(content);
        Map<String, Object> filePart = "application/pdf".equals(contentType)
                ? Map.of("type", "input_file", "filename", filename,
                        "file_data", "data:application/pdf;base64," + base64)
                : Map.of("type", "input_image", "detail", "high",
                        "image_url", "data:" + contentType + ";base64," + base64);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("store", false);
        body.put("instructions", instructions);
        body.put("input", List.of(Map.of(
                "role", "user",
                "content", List.of(filePart, Map.of("type", "input_text",
                        "text", "Read this utility bill and fill in every field of the schema.")))));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "utility_bill",
                "strict", true,
                "schema", schema)));
        return JSON.writeValueAsString(body);
    }

    private String send(String requestJson) {
        try {
            return restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ExtractionException(errorMessage(response.getStatusCode().value(),
                                readQuietly(response.getBody())));
                    })
                    .body(String.class);
        } catch (ExtractionException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ExtractionException("OpenAI didn't answer in time or couldn't be reached. Try again.");
        } catch (RestClientException e) {
            if (e.getCause() instanceof ExtractionException cause) {
                throw cause; // our own status message, wrapped by the client
            }
            throw new ExtractionException("Couldn't talk to OpenAI. Try again.");
        }
    }

    /** Pulls the model's JSON text out of a Responses API answer. */
    String outputText(String responseBody) {
        Map<String, Object> response;
        try {
            response = parseObject(responseBody);
        } catch (IllegalStateException e) {
            throw new ExtractionException("The answer from OpenAI couldn't be understood. Try again.");
        }
        if ("incomplete".equals(response.get("status"))) {
            throw new ExtractionException("OpenAI stopped before finishing this bill. Try again.");
        }
        if (response.get("output") instanceof List<?> output) {
            for (Object item : output) {
                if (item instanceof Map<?, ?> message && "message".equals(message.get("type"))
                        && message.get("content") instanceof List<?> parts) {
                    for (Object part : parts) {
                        if (part instanceof Map<?, ?> p) {
                            if ("refusal".equals(p.get("type"))) {
                                throw new ExtractionException("OpenAI declined to read this file.");
                            }
                            if ("output_text".equals(p.get("type")) && p.get("text") instanceof String text) {
                                return text;
                            }
                        }
                    }
                }
            }
        }
        throw new ExtractionException("OpenAI returned no answer for this bill. Try again.");
    }

    /** Readable message per HTTP status. OpenAI's own error text is included where it helps. */
    static String errorMessage(int status, String body) {
        return switch (status) {
            case 401, 403 -> "OpenAI rejected the API key. Check OPENAI_API_KEY in backend/.env.";
            case 429 -> "OpenAI's rate limit or quota was reached. Wait a minute and try again, or check your "
                    + "OpenAI billing.";
            default -> {
                if (status >= 500) {
                    yield "OpenAI is having problems right now (HTTP %d). Try again later.".formatted(status);
                }
                String detail = openAiErrorMessage(body);
                yield "OpenAI couldn't read this file (HTTP %d)%s".formatted(status,
                        detail == null ? "." : ": " + detail);
            }
        };
    }

    private static String openAiErrorMessage(String body) {
        try {
            Map<String, Object> parsed = parseObject(body);
            if (parsed.get("error") instanceof Map<?, ?> error && error.get("message") instanceof String message) {
                return message.length() > 300 ? message.substring(0, 300) + "…" : message;
            }
        } catch (IllegalStateException ignored) {
            // not JSON
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        try {
            Map<String, Object> value = JSON.readValue(json, Map.class);
            if (value == null) {
                throw new IllegalStateException("empty JSON");
            }
            return value;
        } catch (JacksonException e) {
            throw new IllegalStateException("not JSON");
        }
    }

    private static String readQuietly(InputStream body) {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing classpath resource " + path, e);
        }
    }
}
