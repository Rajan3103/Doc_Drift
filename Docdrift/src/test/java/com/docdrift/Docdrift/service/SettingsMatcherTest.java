package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsMatcherTest {

    @Test
    void defaultBranchOnlyWhenNoBranchesConfigured() {
        assertThat(SettingsMatcher.watchesBranch(List.of(), "main", "main")).isTrue();
        assertThat(SettingsMatcher.watchesBranch(List.of(), "feature/x", "main")).isFalse();
        assertThat(SettingsMatcher.watchesBranch(List.of(), null, "main")).isFalse();
    }

    @Test
    void configuredBranchesSupportGlobs() {
        List<String> patterns = List.of("main", "release/*");
        assertThat(SettingsMatcher.watchesBranch(patterns, "release/1.2", "main")).isTrue();
        assertThat(SettingsMatcher.watchesBranch(patterns, "main", "develop")).isTrue();
        assertThat(SettingsMatcher.watchesBranch(patterns, "develop", "develop")).isFalse();
    }

    @Test
    void docPathsMatchFilesFoldersAndGlobs() {
        List<String> patterns = List.of("README.md", "docs/", "api/**.yaml");
        assertThat(SettingsMatcher.matchesDocPath(patterns, "README.md")).isTrue();
        assertThat(SettingsMatcher.matchesDocPath(patterns, "docs/guide/setup.md")).isTrue();
        assertThat(SettingsMatcher.matchesDocPath(patterns, "api/v1/openapi.yaml")).isTrue();
        assertThat(SettingsMatcher.matchesDocPath(patterns, "frontend/README.md")).isFalse();
    }
}
