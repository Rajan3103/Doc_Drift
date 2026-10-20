package com.docdrift.Docdrift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRepositoryRequest(
        @NotBlank
        @Pattern(regexp = "^https://github\\.com/[A-Za-z0-9-]+/[A-Za-z0-9._-]+/?$",
                message = "must be a GitHub repository URL like https://github.com/owner/repo")
        String url,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$", message = "must be in the form owner/repo")
        String name) {

    /** "owner/repo" taken from the URL, without a trailing slash or .git suffix. */
    public String repositoryFromUrl() {
        String path = url.substring("https://github.com/".length());
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        return path;
    }
}
