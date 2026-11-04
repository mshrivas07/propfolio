package com.propfolio.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.propfolio.documents.TestFiles;
import com.propfolio.documents.TestReadings;

/**
 * The OpenAI client against canned responses. No network calls; no real bills.
 */
class OpenAiBillReaderTest {

    private static final String BASE = "https://openai.test/v1";
    private static final OpenAiProperties PROPS = new OpenAiProperties("test-key", BASE, "test-model",
            Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private OpenAiBillReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new OpenAiBillReader(builder.build(), PROPS);
    }

    @Test
    void sendsAPdfWithTheStrictSchemaAndParsesTheAnswer() {
        server.expect(requestTo(BASE + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.instructions").value(Matchers.containsString("current_charges")))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_file"))
                .andExpect(jsonPath("$.input[0].content[0].filename").value("hydro.pdf"))
                .andExpect(jsonPath("$.input[0].content[0].file_data")
                        .value(Matchers.startsWith("data:application/pdf;base64,")))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                .andRespond(withSuccess(response(TestReadings.hydro("1234 5678-9")), MediaType.APPLICATION_JSON));

        OpenAiBillReader.Reading reading = reader.read(TestFiles.pdf("x"), "application/pdf", "hydro.pdf");

        assertThat(reading.bill().accountNumber()).isEqualTo("1234 5678-9");
        assertThat(reading.bill().currentCharges()).isEqualByComparingTo("120.00");
        assertThat(reading.rawJson()).contains("\"provider_name\"");
        server.verify();
    }

    @Test
    void sendsImagesAsDataUrls() {
        server.expect(requestTo(BASE + "/responses"))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_image"))
                .andExpect(jsonPath("$.input[0].content[0].image_url").value(Matchers.startsWith("data:image/png;base64,")))
                .andRespond(withSuccess(response(TestReadings.hydro("1")), MediaType.APPLICATION_JSON));

        reader.read(TestFiles.png(), "image/png", "scan.png");
        server.verify();
    }

    @Test
    void skipsReasoningItemsBeforeTheMessage() {
        String body = """
                {"status": "completed", "output": [
                  {"type": "reasoning", "summary": []},
                  {"type": "message", "content": [{"type": "output_text", "text": %s}]}
                ]}""".formatted(quote(TestReadings.hydro("42")));
        server.expect(requestTo(BASE + "/responses")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(reader.read(TestFiles.pdf("x"), "application/pdf", "a.pdf").bill().accountNumber()).isEqualTo("42");
    }

    @Test
    void refusalIsAReadableError() {
        String body = """
                {"status": "completed", "output": [{"type": "message",
                  "content": [{"type": "refusal", "refusal": "I can't help with that."}]}]}""";
        server.expect(requestTo(BASE + "/responses")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertFailsWith("declined");
    }

    @Test
    void incompleteAnswerIsAReadableError() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withSuccess(
                "{\"status\": \"incomplete\", \"incomplete_details\": {\"reason\": \"max_output_tokens\"}, \"output\": []}",
                MediaType.APPLICATION_JSON));
        assertFailsWith("stopped before finishing");
    }

    @Test
    void answerThatIsNotJsonIsAReadableError() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withSuccess(response("Total due is $120"), MediaType.APPLICATION_JSON));
        assertFailsWith("couldn't be understood");
    }

    @Test
    void emptyOutputIsAReadableError() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withSuccess("{\"status\": \"completed\", \"output\": []}", MediaType.APPLICATION_JSON));
        assertFailsWith("no answer");
    }

    @Test
    void wrongKeyIs401() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withUnauthorizedRequest());
        assertFailsWith("rejected the API key");
    }

    @Test
    void rateLimitIs429() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withTooManyRequests());
        assertFailsWith("rate limit or quota");
    }

    @Test
    void serverErrorIs5xx() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withServerError());
        assertFailsWith("having problems right now (HTTP 500)");
    }

    @Test
    void badRequestIncludesOpenAisMessage() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"message\": \"The model `test-model` does not exist.\"}}"));
        assertFailsWith("does not exist");
    }

    @Test
    void timeoutIsAReadableError() {
        server.expect(requestTo(BASE + "/responses")).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertFailsWith("didn't answer in time");
    }

    @Test
    void missingKeyFailsBeforeCallingOpenAi() {
        OpenAiBillReader unconfigured = new OpenAiBillReader(RestClient.create(BASE),
                new OpenAiProperties("", BASE, "m", Duration.ofSeconds(1)));

        assertThatThrownBy(() -> unconfigured.read(TestFiles.pdf("x"), "application/pdf", "a.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(PROPS.toString()).doesNotContain("test-key");
    }

    private void assertFailsWith(String messagePart) {
        assertThatThrownBy(() -> reader.read(TestFiles.pdf("x"), "application/pdf", "a.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining(messagePart)
                .hasMessageNotContaining("test-key");
    }

    /** A Responses API answer whose output text is {@code text}. */
    private static String response(String text) {
        return """
                {"id": "resp_test", "status": "completed", "output": [
                  {"type": "message", "role": "assistant",
                   "content": [{"type": "output_text", "text": %s}]}
                ]}""".formatted(quote(text));
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
