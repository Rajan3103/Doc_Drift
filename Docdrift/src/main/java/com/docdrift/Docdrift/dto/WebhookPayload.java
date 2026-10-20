package com.docdrift.Docdrift.dto;

import lombok.Data;
import java.util.List;

@Data
public class WebhookPayload {
    private String ref;
    private String before; // commit SHA before the push (all zeros for a new branch)
    private String after;  // commit SHA after the push (all zeros when a branch is deleted)
    private List<CommitPayload> commits;
    private RepositoryPayload repository;

    @Data
    public static class CommitPayload {
        private String id;
        private String message;
        private List<String> added;
        private List<String> removed;
        private List<String> modified;
    }
    
    @Data
    public static class RepositoryPayload {
        private String full_name;
        private String default_branch;
    }
}
