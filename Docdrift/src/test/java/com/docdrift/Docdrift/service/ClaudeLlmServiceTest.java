package com.docdrift.Docdrift.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs ClaudeLlmService against a local fake of the Messages API. */
class ClaudeLlmServiceTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockWebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private ClaudeLlmService service() {
        return new ClaudeLlmService("test-key", server.url("/").toString(), "claude-opus-5-5", "medium", mapper);
    }

    private static MockResponse messageResponse(String text, String stopReason) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":20}}""".formatted(escaped, stopReason));
    }

    @Test
    void sendsStructuredOutputRequestAndReturnsSuggestionsArray() throws Exception {
        server.enqueue(messageResponse("""
                {"suggestions":[{"filePath":"README.md","driftType":"API_SIGNATURE_MISMATCH","severity":"HIGH",
                "confidenceScore":0.9,"impactedSymbol":"GET /api/orders","isBreakingChange":true,"httpMethod":"GET",
                "endpointPath":"/api/orders","schemaFormat":"GENERAL_PROSE","oldText":"old","suggestedText":"new",
                "reason":"Route changed"}]}""", "end_turn"));

        String json = service().analyze("prompt text").block();

        JsonNode suggestions = mapper.readTree(json);
        assertThat(suggestions.isArray()).isTrue();
        assertThat(suggestions.get(0).get("oldText").asString()).isEqualTo("old");
        assertThat(suggestions.get(0).get("isBreakingChange").asBoolean()).isTrue();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("test-key");
        assertThat(request.getHeader("anthropic-beta")).contains("server-side-fallback-2026-07-01");
        JsonNode body = mapper.readTree(request.getBody().readUtf8());
        assertThat(body.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(body.get("fallbacks").asString()).isEqualTo("default");
        assertThat(body.get("output_config").get("effort").asString()).isEqualTo("medium");
        assertThat(body.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
        assertThat(body.get("output_config").get("format").get("schema").get("properties").has("suggestions")).isTrue();
        assertThat(body.get("messages").get(0).get("content").toString()).contains("prompt text");
    }

    @Test
    void refusalAndTruncationAreErrorsSoTheRouterCanFallBack() {
        server.enqueue(messageResponse("", "refusal"));
        assertThatThrownBy(() -> service().analyze("p").block()).hasMessageContaining("declined");

        server.enqueue(messageResponse("{\"suggestions\":[", "max_tokens"));
        assertThatThrownBy(() -> service().analyze("p").block()).hasMessageContaining("token limit");
    }

    @Test
    void notConfiguredWithoutApiKey() {
        ClaudeLlmService service = new ClaudeLlmService("", server.url("/").toString(), "claude-opus-5-5", "medium", mapper);
        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.analyze("p").block()).hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
