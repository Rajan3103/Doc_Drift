package com.docdrift.Docdrift.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans a unified git diff before it is sent to the LLM: drops lockfiles, generated output and binaries,
 * and caps the size so large pushes stay within the model's input limits.
 */
public final class DiffPreprocessor {

    private static final Pattern FILE_HEADER = Pattern.compile("^diff --git a/(.+?) b/(.+)$", Pattern.MULTILINE);

    private static final List<String> LOCKFILES = List.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json", "poetry.lock",
            "pipfile.lock", "cargo.lock", "go.sum", "composer.lock", "gemfile.lock", "gradle.lockfile");

    private static final List<String> GENERATED_DIRS = List.of(
            "node_modules/", "vendor/", "dist/", "build/", "target/", "out/", ".next/", "coverage/");

    private static final List<String> BINARY_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp", ".svg", ".pdf", ".zip", ".jar", ".woff", ".woff2",
            ".ttf", ".eot", ".mp4", ".mp3", ".class", ".exe", ".dll", ".so");

    private DiffPreprocessor() {
    }

    /** A cleaned diff plus what was left out of it. */
    public record Result(String diff, List<String> changedFiles, List<String> skippedFiles,
                         List<String> truncatedFiles, boolean hasCodeChanges) {
    }

    public static Result process(String rawDiff, int maxTotalChars, int maxFileChars) {
        List<String> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> truncated = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        boolean hasCodeChanges = false;

        for (FileSection section : split(rawDiff == null ? "" : rawDiff)) {
            if (isIgnored(section.path) || section.text.contains("\nBinary files ")) {
                skipped.add(section.path);
                continue;
            }
            changed.add(section.path);
            if (!DocSelector.isDocumentation(section.path)) {
                hasCodeChanges = true;
            }

            String text = section.text;
            if (text.length() > maxFileChars) {
                text = text.substring(0, maxFileChars) + "\n... [diff for " + section.path + " truncated]\n";
            }
            if (out.length() + text.length() > maxTotalChars) {
                truncated.add(section.path);
                continue;
            }
            out.append(text);
        }

        if (!truncated.isEmpty()) {
            out.append("\n... [diff size limit reached; changes to these files were omitted: ")
                    .append(String.join(", ", truncated)).append("]\n");
        }
        return new Result(out.toString(), changed, skipped, truncated, hasCodeChanges);
    }

    static boolean isIgnored(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (LOCKFILES.contains(name) || name.endsWith(".min.js") || name.endsWith(".min.css") || name.endsWith(".map")) {
            return true;
        }
        for (String dir : GENERATED_DIRS) {
            if (lower.startsWith(dir) || lower.contains("/" + dir)) {
                return true;
            }
        }
        return BINARY_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private record FileSection(String path, String text) {
    }

    private static List<FileSection> split(String diff) {
        List<FileSection> sections = new ArrayList<>();
        Matcher matcher = FILE_HEADER.matcher(diff);
        List<Integer> starts = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            paths.add(matcher.group(2));
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : diff.length();
            sections.add(new FileSection(paths.get(i), diff.substring(start, end)));
        }
        return sections;
    }
}
