package com.docdrift.Docdrift.service;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/** Applies a repository's branch and documentation path settings. Patterns may use globs such as release/* or docs/**.md. */
public final class SettingsMatcher {

    private SettingsMatcher() {
    }

    /** With no patterns configured, only the default branch is watched. */
    public static boolean watchesBranch(List<String> patterns, String branch, String defaultBranch) {
        if (branch == null) {
            return false;
        }
        if (patterns == null || patterns.isEmpty()) {
            return defaultBranch == null || branch.equals(defaultBranch);
        }
        return patterns.stream().anyMatch(pattern -> globMatches(pattern, branch));
    }

    /** A pattern is an exact file path, a folder ending in "/", or a glob. */
    public static boolean matchesDocPath(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pattern.endsWith("/")
                ? path.startsWith(pattern)
                : pattern.equals(path) || globMatches(pattern, path));
    }

    private static boolean globMatches(String pattern, String value) {
        if (pattern.equals(value)) {
            return true;
        }
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(Path.of(value));
        } catch (IllegalArgumentException e) { // includes InvalidPathException for malformed patterns
            return false;
        }
    }
}
