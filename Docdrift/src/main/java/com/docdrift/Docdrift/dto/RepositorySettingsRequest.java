package com.docdrift.Docdrift.dto;

import com.docdrift.Docdrift.model.Severity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Per-repository settings. slackWebhookUrl: null keeps the current value, "" removes it.
 * Slack URLs are restricted to hooks.slack.com so the server cannot be pointed at arbitrary addresses.
 */
public record RepositorySettingsRequest(
        @NotNull @Size(max = 50)
        List<@NotBlank @Size(max = 200) @Pattern(regexp = "^[^/\\\\\\s][^\\\\\\s]*$",
                message = "must be a relative path without spaces") String> docPaths,

        @NotNull @Size(max = 50)
        List<@NotBlank @Size(max = 200) @Pattern(regexp = "^[A-Za-z0-9._/*-]+$",
                message = "must be a branch name or glob like release/*") String> watchedBranches,

        @NotNull Severity minSeverity,

        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minConfidence,

        @NotNull Boolean analyzePullRequests,

        @Pattern(regexp = "^$|^https://hooks\\.slack\\.com/services/[A-Za-z0-9/_-]+$",
                message = "must be a Slack incoming webhook URL (https://hooks.slack.com/services/...)")
        String slackWebhookUrl,

        @Email @Size(max = 254) String alertEmail) {
}
