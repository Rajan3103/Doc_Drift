package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.GeminiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiLlmServiceTest {

    private static GeminiResponse response(String text, String finishReason) {
        GeminiResponse.Part part = new GeminiResponse.Part();
        part.setText(text);
        GeminiResponse.Content content = new GeminiResponse.Content();
        content.setParts(text == null ? List.of() : List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate();
        candidate.setContent(content);
        candidate.setFinishReason(finishReason);
        GeminiResponse response = new GeminiResponse();
        response.setCandidates(List.of(candidate));
        return response;
    }

    @Test
    void returnsJsonAndStripsStrayCodeFences() {
        assertEquals("[{\"a\":1}]", GeminiLlmService.extractJson(response("[{\"a\":1}]", "STOP")));
        assertEquals("[]", GeminiLlmService.extractJson(response("```json\n[]\n```", "STOP")));
    }

    @Test
    void reportsMissingOutputAsAnErrorInsteadOfNoDrift() {
        assertThrows(IllegalStateException.class, () -> GeminiLlmService.extractJson(new GeminiResponse()));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> GeminiLlmService.extractJson(response(null, "SAFETY")));
        assertEquals("Gemini returned no content (finish reason: SAFETY)", e.getMessage());
    }
}
