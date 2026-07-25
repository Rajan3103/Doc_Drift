package com.docdrift.Docdrift.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GitHubService {

    private final WebClient webClient;

    public GitHubService(WebClient.Builder webClientBuilder,
                         @Value("${github.api.url}") String githubApiUrl,
                         @Value("${github.api.token}") String githubToken) {
        this.webClient = webClientBuilder
                .baseUrl(githubApiUrl)
                .defaultHeader("Authorization", "Bearer " + githubToken)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    public Mono<String> getCommitDiff(String repositoryName, String commitSha) {
        return this.webClient.get()
                .uri("/repos/{repo}/commits/{sha}", repositoryName, commitSha)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just(""));
    }

    public Mono<String> getFileContent(String repositoryName, String filePath, String ref) {
        return this.webClient.get()
                .uri("/repos/{repo}/contents/{path}?ref={ref}", repositoryName, filePath, ref)
                .header("Accept", "application/vnd.github.v3.raw")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just(""));
    }
}
