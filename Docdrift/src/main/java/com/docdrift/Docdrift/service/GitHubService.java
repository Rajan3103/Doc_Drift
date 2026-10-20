package com.docdrift.Docdrift.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class GitHubService {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final Duration retryBackoff;

    public GitHubService(WebClient.Builder webClientBuilder,
                         @Value("${github.api.url}") String githubApiUrl,
                         @Value("${github.api.token}") String githubToken,
                         @Value("${docdrift.http.retry-backoff:2s}") Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
        // clone() so the GitHub token is not added to the shared builder used for other APIs
        this.webClient = webClientBuilder.clone()
                .baseUrl(githubApiUrl)
                .defaultHeader("Authorization", "Bearer " + githubToken)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    public Mono<String> getCommitDiff(String repositoryName, String commitSha) {
        return getDiff(repoUri(repositoryName, List.of("commits", commitSha), null));
    }

    /** Combined diff of everything between two commits, e.g. all commits in one push. */
    public Mono<String> getCompareDiff(String repositoryName, String baseSha, String headSha) {
        return getDiff(repoUri(repositoryName, List.of("compare", baseSha + "..." + headSha), null));
    }

    private Mono<String> getDiff(Function<UriBuilder, URI> uri) {
        return this.webClient.get()
                .uri(uri)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .defaultIfEmpty("")
                .retryWhen(HttpRetry.transientErrors(retryBackoff));
    }

    /** Returns the file's text, or an empty string if it does not exist at that ref. */
    public Mono<String> getFileContent(String repositoryName, String filePath, String ref) {
        return this.webClient.get()
                .uri(contentsUri(repositoryName, filePath, ref))
                .header("Accept", "application/vnd.github.v3.raw")
                .retrieve()
                .bodyToMono(String.class)
                .defaultIfEmpty("")
                .retryWhen(HttpRetry.transientErrors(retryBackoff))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(""));
    }

    /** SHA of the latest commit on the repository's default branch. */
    public Mono<String> getDefaultBranchHeadSha(String repositoryName) {
        return getJson(repoUri(repositoryName, List.of(), null))
                .map(repo -> (String) repo.get("default_branch"))
                .flatMap(branch -> getJson(repoUri(repositoryName, List.of("commits", branch), null)))
                .map(commit -> (String) commit.get("sha"));
    }

    /** Paths of all files in the repository at the given commit. */
    public Mono<List<String>> listFiles(String repositoryName, String sha) {
        return webClient.get()
                .uri(builder -> repoUri(repositoryName, List.of("git", "trees", sha), null).apply(
                        builder.queryParam("recursive", "1")))
                .retrieve()
                .bodyToMono(JSON_MAP)
                .retryWhen(HttpRetry.transientErrors(retryBackoff))
                .map(tree -> ((List<?>) tree.getOrDefault("tree", List.of())).stream()
                        .map(entry -> (Map<?, ?>) entry)
                        .filter(entry -> "blob".equals(entry.get("type")))
                        .map(entry -> (String) entry.get("path"))
                        .toList());
    }

    /**
     * Opens a pull request that replaces oldText with newText in filePath on the repository's default branch.
     * Returns the PR's html_url. Errors (including oldText not being found) are propagated to the caller.
     */
    public Mono<String> createDocFixPullRequest(String repositoryName, String filePath, String oldText,
                                                String newText, String branchName, String title, String body) {
        return getJson(repoUri(repositoryName, List.of(), null))
                .map(repo -> (String) repo.get("default_branch"))
                .flatMap(baseBranch -> getJson(contentsUri(repositoryName, filePath, baseBranch))
                        .flatMap(file -> {
                            String current = decodeContent((String) file.get("content"));
                            int index = current.indexOf(oldText);
                            if (index < 0) {
                                return Mono.error(new IllegalStateException(
                                        "The outdated text was not found in " + filePath + " on " + baseBranch
                                                + "; the file may have changed since the scan."));
                            }
                            String updated = current.substring(0, index) + newText + current.substring(index + oldText.length());

                            return getJson(repoUri(repositoryName, List.of("git", "ref", "heads", baseBranch), null))
                                    .map(ref -> (String) ((Map<?, ?>) ref.get("object")).get("sha"))
                                    .flatMap(baseSha -> postJson(repoUri(repositoryName, List.of("git", "refs"), null),
                                            Map.of("ref", "refs/heads/" + branchName, "sha", baseSha)))
                                    .then(putJson(contentsUri(repositoryName, filePath, null), Map.of(
                                            "message", title,
                                            "content", Base64.getEncoder().encodeToString(updated.getBytes(StandardCharsets.UTF_8)),
                                            "sha", file.get("sha"),
                                            "branch", branchName)))
                                    .then(postJson(repoUri(repositoryName, List.of("pulls"), null), Map.of(
                                            "title", title,
                                            "head", branchName,
                                            "base", baseBranch,
                                            "body", body)))
                                    .map(pr -> (String) pr.get("html_url"));
                        }));
    }

    private static String decodeContent(String base64Content) {
        // GitHub wraps base64 content in newlines, which the MIME decoder tolerates
        return new String(Base64.getMimeDecoder().decode(base64Content), StandardCharsets.UTF_8);
    }

    private static Function<UriBuilder, URI> contentsUri(String repositoryName, String filePath, String ref) {
        List<String> segments = new ArrayList<>(List.of("contents"));
        segments.addAll(List.of(filePath.split("/")));
        return repoUri(repositoryName, segments, ref);
    }

    /**
     * Builds /repos/{owner}/{repo}/{segment}... with every part passed as a URI variable.
     * Passing "owner/repo" or "docs/api.md" as a single variable would encode the slash as %2F, which GitHub rejects.
     */
    private static Function<UriBuilder, URI> repoUri(String repositoryName, List<String> segments, String ref) {
        return builder -> {
            List<Object> vars = new ArrayList<>(List.of(repositoryName.split("/", 2)));
            StringBuilder template = new StringBuilder("/repos/{owner}/{repo}");
            for (int i = 0; i < segments.size(); i++) {
                template.append("/{s").append(i).append('}');
                vars.add(segments.get(i));
            }
            builder.path(template.toString());
            if (ref != null) {
                builder.queryParam("ref", "{ref}");
                vars.add(ref);
            }
            return builder.build(vars.toArray());
        };
    }

    // Only reads are retried; repeating a write could create duplicate branches or pull requests
    private Mono<Map<String, Object>> getJson(Function<UriBuilder, URI> uri) {
        return webClient.get().uri(uri).retrieve().bodyToMono(JSON_MAP)
                .retryWhen(HttpRetry.transientErrors(retryBackoff));
    }

    private Mono<Map<String, Object>> postJson(Function<UriBuilder, URI> uri, Object requestBody) {
        return webClient.post().uri(uri).bodyValue(requestBody).retrieve().bodyToMono(JSON_MAP);
    }

    private Mono<Map<String, Object>> putJson(Function<UriBuilder, URI> uri, Object requestBody) {
        return webClient.put().uri(uri).bodyValue(requestBody).retrieve().bodyToMono(JSON_MAP);
    }
}
