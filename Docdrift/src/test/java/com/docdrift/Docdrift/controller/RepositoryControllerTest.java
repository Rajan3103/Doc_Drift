package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepositoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;

    @BeforeEach
    void clean() {
        driftSuggestionRepository.deleteAll();
        driftReportRepository.deleteAll();
        webhookEventRepository.deleteAll();
        repositoryConfigRepository.deleteAll();
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/repos").header("X-API-Key", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsRepositoryAndIgnoresServerControlledFields() throws Exception {
        create("""
                {"url":"https://github.com/Owner/My-Repo.git","name":"owner/my-repo","id":999,"status":"DRIFTED"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Owner/My-Repo"))
                .andExpect(jsonPath("$.url").value("https://github.com/Owner/My-Repo"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.id").value(not(999)));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        create("""
                {"url":"https://gitlab.com/owner/repo","name":"owner/repo"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("url must be a GitHub repository URL")));
        create("""
                {"url":"https://github.com/owner/repo","name":""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name")));
        create("""
                {"url":"https://github.com/owner/repo","name":"someone/else"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("does not match the URL")));
        create("not json").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateRepository() throws Exception {
        create("""
                {"url":"https://github.com/owner/repo","name":"owner/repo"}""").andExpect(status().isCreated());
        create("""
                {"url":"https://github.com/OWNER/REPO/","name":"OWNER/REPO"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("already tracked")));
    }

    @Test
    void listsRepositoriesInPages() throws Exception {
        for (String name : new String[]{"o/charlie", "o/alpha", "o/bravo"}) {
            create("{\"url\":\"https://github.com/" + name + "\",\"name\":\"" + name + "\"}").andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/repos?page=0&size=2").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("o/alpha"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/repos?page=1&size=2").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.content[0].name").value("o/charlie"));
    }
}
