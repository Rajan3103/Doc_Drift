package com.docdrift.Docdrift.service;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic API contract check: compares the REST endpoints declared in Spring controller source code with the
 * paths in an OpenAPI / Swagger specification. Unlike the LLM analysis, its findings are exact facts.
 */
public final class ApiContractChecker {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");
    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(class|interface)\\s+\\w+");
    private static final Pattern MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch|Request)Mapping\\b(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern NON_PATH_ATTRIBUTE = Pattern.compile(
            "\\b(produces|consumes|params|headers|name)\\s*=\\s*(\\{[^}]*\\}|\\[[^\\]]*\\]|\"[^\"]*\"|[\\w.]+)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.(\\w+)");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*}");

    private ApiContractChecker() {
    }

    /** An HTTP endpoint. Two endpoints are equal when they differ only in path variable names. */
    public record Endpoint(String method, String path) {

        public String key() {
            return method + " " + PATH_VARIABLE.matcher(path).replaceAll("{}");
        }

        public String display() {
            return method + " " + path;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Endpoint endpoint && key().equals(endpoint.key());
        }

        @Override
        public int hashCode() {
            return key().hashCode();
        }
    }

    public enum FindingKind { REMOVED_BUT_DOCUMENTED, ADDED_BUT_UNDOCUMENTED }

    public record Finding(FindingKind kind, Endpoint endpoint, String sourceFile) {

        /** A sentence handed to the LLM as a verified fact, and used as the reason of rule-based suggestions. */
        public String describe(String specPath) {
            return kind == FindingKind.REMOVED_BUT_DOCUMENTED
                    ? endpoint.display() + " was removed from " + sourceFile + " but is still documented in " + specPath + "."
                    : endpoint.display() + " was added in " + sourceFile + " but is not documented in " + specPath + ".";
        }
    }

    /** True for source files the endpoint extractor understands. */
    public static boolean isControllerSource(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".kt");
    }

    /** Endpoints declared with Spring MVC / WebFlux mapping annotations in one source file. */
    public static Set<Endpoint> endpointsInSpringSource(String source) {
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        if (source == null || source.isBlank()) {
            return endpoints;
        }
        String code = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll("")).replaceAll("");

        Matcher declaration = TYPE_DECLARATION.matcher(code);
        int classStart = declaration.find() ? declaration.start() : 0;

        // A @RequestMapping before the class declaration sets the prefix for every method in it
        List<String> prefixes = new ArrayList<>(List.of(""));
        Matcher classMapping = MAPPING.matcher(code.substring(0, classStart));
        while (classMapping.find()) {
            if ("Request".equals(classMapping.group(1))) {
                prefixes = paths(classMapping.group(2));
            }
        }

        Matcher mapping = MAPPING.matcher(code);
        mapping.region(classStart, code.length());
        while (mapping.find()) {
            String kind = mapping.group(1);
            String args = mapping.group(2);
            List<String> methods = new ArrayList<>();
            if ("Request".equals(kind)) {
                Matcher requestMethod = REQUEST_METHOD.matcher(args == null ? "" : args);
                while (requestMethod.find()) {
                    methods.add(requestMethod.group(1).toUpperCase(Locale.ROOT));
                }
            } else {
                methods.add(kind.toUpperCase(Locale.ROOT));
            }
            for (String prefix : prefixes) {
                for (String path : paths(args)) {
                    for (String method : methods) {
                        if (HTTP_METHODS.contains(method)) {
                            endpoints.add(new Endpoint(method, normalize(prefix + "/" + path)));
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static List<String> paths(String args) {
        if (args == null || args.isBlank()) {
            return List.of("");
        }
        String pathArgs = NON_PATH_ATTRIBUTE.matcher(args).replaceAll("");
        List<String> paths = new ArrayList<>();
        Matcher literal = STRING_LITERAL.matcher(pathArgs);
        while (literal.find()) {
            paths.add(literal.group(1));
        }
        return paths.isEmpty() ? List.of("") : paths;
    }

    /**
     * Endpoints in an OpenAPI 3 (servers[0].url path is the prefix) or Swagger 2 (basePath is the prefix) document,
     * in YAML or JSON. Throws IllegalArgumentException when the content is not a readable specification.
     */
    public static Set<Endpoint> endpointsInSpec(String content) {
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Specification is not valid YAML/JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> spec) || !(spec.get("paths") instanceof Map<?, ?> paths)) {
            throw new IllegalArgumentException("Specification has no 'paths' section");
        }

        String prefix = "";
        if (spec.get("basePath") instanceof String basePath) {
            prefix = basePath;
        } else if (spec.get("servers") instanceof List<?> servers && !servers.isEmpty()
                && servers.getFirst() instanceof Map<?, ?> server && server.get("url") instanceof String url) {
            prefix = urlPath(url);
        }

        Set<Endpoint> endpoints = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : paths.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> operations)) {
                continue;
            }
            for (Object method : operations.keySet()) {
                String upper = String.valueOf(method).toUpperCase(Locale.ROOT);
                if (HTTP_METHODS.contains(upper)) {
                    endpoints.add(new Endpoint(upper, normalize(prefix + "/" + entry.getKey())));
                }
            }
        }
        return endpoints;
    }

    private static String urlPath(String url) {
        if (url.startsWith("/")) {
            return url;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return "";
        }
        int pathStart = url.indexOf('/', scheme + 3);
        return pathStart < 0 ? "" : url.substring(pathStart);
    }

    static String normalize(String path) {
        String normalized = ("/" + path).replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Compares endpoints before and after a change with the specification.
     *
     * @param baseByFile endpoints per changed source file before the change
     * @param headByFile endpoints per changed source file after the change
     */
    public static List<Finding> compare(Map<String, Set<Endpoint>> baseByFile, Map<String, Set<Endpoint>> headByFile,
                                        Set<Endpoint> specEndpoints) {
        Map<Endpoint, String> before = index(baseByFile);
        Map<Endpoint, String> after = index(headByFile);
        List<Finding> findings = new ArrayList<>();

        // Compared across all changed files, so an endpoint moved between controllers is neither added nor removed
        before.forEach((endpoint, file) -> {
            if (!after.containsKey(endpoint) && specEndpoints.contains(endpoint)) {
                findings.add(new Finding(FindingKind.REMOVED_BUT_DOCUMENTED, endpoint, file));
            }
        });
        after.forEach((endpoint, file) -> {
            if (!before.containsKey(endpoint) && !specEndpoints.contains(endpoint)) {
                findings.add(new Finding(FindingKind.ADDED_BUT_UNDOCUMENTED, endpoint, file));
            }
        });
        return findings;
    }

    private static Map<Endpoint, String> index(Map<String, Set<Endpoint>> byFile) {
        Map<Endpoint, String> index = new LinkedHashMap<>();
        byFile.forEach((file, endpoints) -> endpoints.forEach(endpoint -> index.putIfAbsent(endpoint, file)));
        return index;
    }

    /** True when an LLM suggestion already covers this endpoint (spec paths may omit the server prefix). */
    public static boolean coveredBy(Endpoint endpoint, String httpMethod, String endpointPath) {
        if (httpMethod == null || endpointPath == null || endpointPath.isBlank()
                || !endpoint.method().equalsIgnoreCase(httpMethod.trim())) {
            return false;
        }
        String mine = endpoint.key().substring(endpoint.method().length() + 1);
        String theirs = new Endpoint(endpoint.method(), normalize(endpointPath.trim())).key()
                .substring(endpoint.method().length() + 1);
        return mine.equals(theirs) || mine.endsWith(theirs) || theirs.endsWith(mine);
    }
}
