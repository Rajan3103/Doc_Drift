package com.docdrift.Docdrift.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which documentation files in a repository are worth sending to the LLM for a given diff,
 * so unrelated docs do not cost an LLM call on every push.
 */
public final class DocSelector {

    private static final List<String> DOC_EXTENSIONS = List.of(".md", ".mdx", ".rst", ".adoc");

    // Historical or legal docs that should not be rewritten to match new code
    private static final List<String> EXCLUDED_NAMES = List.of(
            "changelog", "history", "license", "licence", "code_of_conduct", "pull_request_template", "notice");

    private static final Pattern IDENTIFIER = Pattern.compile("/?[A-Za-z_][A-Za-z0-9_.\\-/]{3,}");

    private static final Set<String> STOP_WORDS = Set.of(
            "public", "private", "protected", "static", "final", "return", "import", "package", "class",
            "interface", "void", "string", "this", "null", "true", "false", "const", "function", "export",
            "default", "async", "await", "else", "while", "throw", "throws", "new", "self", "from", "with",
            "override", "boolean", "integer", "long", "double", "list", "data", "value", "name", "type");

    private DocSelector() {
    }

    public static boolean isApiSpec(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        return (name.contains("openapi") || name.contains("swagger"))
                && (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"));
    }

    public static boolean isDocumentation(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return isApiSpec(path) || DOC_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static boolean isRootReadme(String path) {
        return !path.contains("/") && path.toLowerCase(Locale.ROOT).startsWith("readme");
    }

    /** Documentation files from a repository tree that could be analysed, most important first. */
    public static List<String> candidates(List<String> repoPaths, int limit) {
        return repoPaths.stream()
                .filter(DocSelector::isDocumentation)
                .filter(path -> !DiffPreprocessor.isIgnored(path))
                .filter(path -> !path.toLowerCase(Locale.ROOT).startsWith(".github/"))
                .filter(path -> {
                    String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    return EXCLUDED_NAMES.stream().noneMatch(name::startsWith);
                })
                .sorted(Comparator.comparingInt(DocSelector::priority).thenComparing(Comparator.naturalOrder()))
                .limit(limit)
                .toList();
    }

    private static int priority(String path) {
        if (isApiSpec(path)) return 0;
        if (isRootReadme(path)) return 1;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("docs/") || lower.startsWith("doc/")) return 2;
        if (!path.contains("/")) return 3;
        return 4;
    }

    /**
     * Words a doc must mention to be affected by the diff: identifiers on removed/changed lines
     * and the names of changed source files.
     */
    public static Set<String> keywords(String diff, List<String> changedFiles) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String file : changedFiles) {
            String name = file.substring(file.lastIndexOf('/') + 1);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            if (base.length() >= 4 && !isDocumentation(file)) {
                keywords.add(base);
            }
        }
        for (String line : diff.split("\n")) {
            if (line.startsWith("-") && !line.startsWith("---")) {
                Matcher matcher = IDENTIFIER.matcher(line);
                while (matcher.find()) {
                    String token = matcher.group();
                    if (!STOP_WORDS.contains(token.toLowerCase(Locale.ROOT))) {
                        keywords.add(token);
                    }
                }
            }
        }
        return keywords;
    }

    /** API specs and the root README are always checked; other docs only if they mention something that changed. */
    public static boolean isRelevant(String path, String content, Set<String> keywords) {
        if (isApiSpec(path) || isRootReadme(path)) {
            return true;
        }
        return keywords.stream().anyMatch(content::contains);
    }
}
