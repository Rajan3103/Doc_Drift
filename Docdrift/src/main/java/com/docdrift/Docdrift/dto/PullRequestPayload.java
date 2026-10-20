package com.docdrift.Docdrift.dto;

import lombok.Data;

/** The parts of GitHub's pull_request webhook payload that DocDrift uses. */
@Data
public class PullRequestPayload {
    private String action; // opened, synchronize, reopened, ready_for_review, closed, ...
    private Integer number;
    private PullRequest pull_request;
    private WebhookPayload.RepositoryPayload repository;

    @Data
    public static class PullRequest {
        private Boolean draft;
        private Ref head;
        private Ref base;
    }

    @Data
    public static class Ref {
        private String sha;
        private String ref; // branch name
        private WebhookPayload.RepositoryPayload repo;
    }
}
