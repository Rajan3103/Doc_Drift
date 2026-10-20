package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndWebhookTest {

    private static final String PUSH_BODY = """
            {"ref":"refs/heads/main","repository":{"full_name":"%s"},
             "commits":[{"id":"abc123","message":"change"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryConfigRepository repositoryConfigRepository;

    @MockitoBean
    private DiffAnalyzerService diffAnalyzerService;

    @Test
    void dashboardApiRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports").header("X-API-Key", "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports").header("X-API-Key", "test-api-key")).andExpect(status().isOk());
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/reports")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/api/reports")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookRejectsMissingOrInvalidSignature() throws Exception {
        String body = PUSH_BODY.formatted("someone/repo");
        mockMvc.perform(post("/api/webhooks/github").contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/github").contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)).content(body))
                .andExpect(status().isUnauthorized());
        verify(diffAnalyzerService, never()).processWebhookEvent(any());
    }

    @Test
    void webhookIgnoresUntrackedRepository() throws Exception {
        String body = PUSH_BODY.formatted("stranger/untracked");
        mockMvc.perform(signedPush(body)).andExpect(status().isAccepted());
        verify(diffAnalyzerService, never()).processWebhookEvent(any());
    }

    @Test
    void webhookProcessesSignedPushForTrackedRepository() throws Exception {
        RepositoryConfig config = new RepositoryConfig();
        config.setName("Owner/Tracked");
        config.setUrl("https://github.com/Owner/Tracked");
        repositoryConfigRepository.save(config);

        mockMvc.perform(signedPush(PUSH_BODY.formatted("owner/tracked"))).andExpect(status().isOk());
        verify(diffAnalyzerService).processWebhookEvent(any());
    }

    private static org.springframework.test.web.servlet.RequestBuilder signedPush(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return post("/api/webhooks/github").contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "push")
                .header("X-Hub-Signature-256", signature)
                .content(body);
    }
}
