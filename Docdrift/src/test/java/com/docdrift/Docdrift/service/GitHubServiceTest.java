package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubServiceTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    /** Fake GitHub API: answers each endpoint the PR flow calls with a canned JSON response. */
    private GitHubService serviceWithDocContent(String docContent) {
        String encoded = Base64.getMimeEncoder().encodeToString(docContent.getBytes(StandardCharsets.UTF_8));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String path = request.url().getRawPath();
            String json;
            if (path.equals("/repos/owner/repo")) {
                json = "{\"default_branch\":\"main\"}";
            } else if (path.equals("/repos/owner/repo/contents/docs/api.md")) {
                json = request.method().name().equals("GET")
                        ? "{\"sha\":\"file-sha\",\"content\":\"" + encoded.replace("\r\n", "\\n") + "\"}"
                        : "{}";
            } else if (path.equals("/repos/owner/repo/git/ref/heads/main")) {
                json = "{\"object\":{\"sha\":\"base-sha\"}}";
            } else if (path.equals("/repos/owner/repo/git/refs")) {
                json = "{}";
            } else if (path.equals("/repos/owner/repo/pulls")) {
                json = "{\"html_url\":\"https://github.com/owner/repo/pull/7\"}";
            } else {
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json).build());
        });
        // Shared builder: the GitHub token must not leak onto other clients built from it
        GitHubService service = new GitHubService(builder, "https://api.github.com", "token", Duration.ofMillis(1));
        WebClient other = builder.build();
        other.get().uri("https://example.com/").retrieve().toBodilessEntity().onErrorResume(e -> Mono.empty()).block();
        assertTrue(requests.getLast().headers().get(HttpHeaders.AUTHORIZATION) == null);
        requests.clear();
        return service;
    }

    @Test
    void createsBranchCommitAndPullRequestWithUnencodedSlashes() {
        GitHubService service = serviceWithDocContent("# API\nGET /old/path\n");

        String prUrl = service.createDocFixPullRequest("owner/repo",
                List.of(new GitHubService.TextEdit("docs/api.md", "GET /old/path", "GET /new/path")),
                "docdrift/suggestion-1", "docs: fix", "body").block();

        assertEquals("https://github.com/owner/repo/pull/7", prUrl);
        assertEquals(List.of(
                "GET /repos/owner/repo",
                "GET /repos/owner/repo/contents/docs/api.md",
                "GET /repos/owner/repo/git/ref/heads/main",
                "POST /repos/owner/repo/git/refs",
                "PUT /repos/owner/repo/contents/docs/api.md",
                "POST /repos/owner/repo/pulls"),
                requests.stream().map(r -> r.method().name() + " " + r.url().getRawPath()).toList());
        assertEquals("ref=main", requests.get(1).url().getRawQuery());
    }

    @Test
    void failsWhenOutdatedTextIsNoLongerInFile() {
        GitHubService service = serviceWithDocContent("# API\nalready updated\n");

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                service.createDocFixPullRequest("owner/repo",
                        List.of(new GitHubService.TextEdit("docs/api.md", "GET /old/path", "GET /new/path")),
                        "b", "t", "body").block());
        assertTrue(e.getMessage().contains("not found"));
        // Nothing is written to the repository
        assertTrue(requests.stream().noneMatch(r -> !r.method().name().equals("GET")));
    }

    @Test
    void retriesTemporaryFailuresButNotMissingFiles() {
        int[] calls = {0};
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            calls[0]++;
            if (request.url().getRawPath().endsWith("/missing.md")) {
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
            }
            // Fail twice with a rate limit, then succeed
            return Mono.just(calls[0] <= 2
                    ? ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build()
                    : ClientResponse.create(HttpStatus.OK).body("# Docs").build());
        });
        GitHubService service = new GitHubService(builder, "https://api.github.com", "token", Duration.ofMillis(1));

        assertEquals("# Docs", service.getFileContent("owner/repo", "README.md", "sha").block());
        assertEquals(3, calls[0]);

        calls[0] = 0;
        assertEquals("", service.getFileContent("owner/repo", "missing.md", "sha").block());
        assertEquals(1, calls[0]);
    }

    @Test
    void listsOnlyFilesFromRecursiveTree() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"tree\":[{\"path\":\"docs\",\"type\":\"tree\"},"
                            + "{\"path\":\"docs/api.md\",\"type\":\"blob\"},{\"path\":\"README.md\",\"type\":\"blob\"}]}")
                    .build());
        });
        GitHubService service = new GitHubService(builder, "https://api.github.com", "token", Duration.ofMillis(1));

        assertEquals(List.of("docs/api.md", "README.md"), service.listFiles("owner/repo", "abc").block());
        assertEquals("/repos/owner/repo/git/trees/abc", requests.getFirst().url().getRawPath());
        assertEquals("recursive=1", requests.getFirst().url().getRawQuery());
    }

    @Test
    void severalEditsToOneFileAreWrittenOnce() {
        GitHubService service = serviceWithDocContent("# API\nGET /old/path\nPOST /old/create\n");

        service.createDocFixPullRequest("owner/repo", List.of(
                        new GitHubService.TextEdit("docs/api.md", "GET /old/path", "GET /new/path"),
                        new GitHubService.TextEdit("docs/api.md", "POST /old/create", "POST /new/create")),
                "b", "t", "body").block();

        assertEquals(1, requests.stream().filter(r -> r.method().name().equals("PUT")).count());
        assertEquals(1, requests.stream().filter(r -> r.method().name().equals("GET")
                && r.url().getRawPath().endsWith("/contents/docs/api.md")).count());
    }

    @Test
    void pullRequestCommentIsUpdatedInsteadOfDuplicated() {
        List<ClientRequest> calls = new ArrayList<>();
        String[] existing = {"[{\"id\": 5, \"body\": \"unrelated\"}]"};
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            calls.add(request);
            String json = request.method().name().equals("GET") ? existing[0] : "{}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(json).build());
        });
        GitHubService service = new GitHubService(builder, "https://api.github.com", "token", Duration.ofMillis(1));

        service.upsertPullRequestComment("owner/repo", 3, "<!-- m -->", "<!-- m --> first").block();
        assertEquals("POST /repos/owner/repo/issues/3/comments",
                calls.getLast().method().name() + " " + calls.getLast().url().getRawPath());

        existing[0] = "[{\"id\": 5, \"body\": \"unrelated\"}, {\"id\": 77, \"body\": \"<!-- m --> first\"}]";
        service.upsertPullRequestComment("owner/repo", 3, "<!-- m -->", "<!-- m --> second").block();
        assertEquals("PATCH /repos/owner/repo/issues/comments/77",
                calls.getLast().method().name() + " " + calls.getLast().url().getRawPath());
    }
}
