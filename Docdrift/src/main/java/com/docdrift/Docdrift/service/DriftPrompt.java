package com.docdrift.Docdrift.service;

import java.util.List;

/** The drift-analysis instructions shared by every LLM provider. Each provider enforces the output shape itself. */
public final class DriftPrompt {

    private DriftPrompt() {
    }

    /**
     * @param contractFacts findings from DocDrift's deterministic API contract check; they are verified facts the
     *                      model should explain and fix rather than re-derive (may be empty)
     */
    public static String build(String filePath, String diff, String docContent, List<String> contractFacts) {
        StringBuilder prompt = new StringBuilder()
                .append("You are an API Contract, Schema, and Documentation Drift Detection Engine for software repositories.\n")
                .append("Analyze the provided Git Code Diff against the target documentation or API specification file (")
                .append(filePath).append(").\n\n")
                .append("### Objectives:\n")
                .append("1. Identify code symbols (REST endpoints, HTTP methods, request/response DTOs, parameters, validation annotations, config keys, database schemas) modified in the diff.\n")
                .append("2. Check if the diff modifies an API Contract (e.g. route path, HTTP method, required/optional fields, types, status codes, OpenAPI/Swagger YAML/JSON schemas, or Markdown API reference tables).\n")
                .append("3. Determine whether any detected drift represents a BREAKING CHANGE for API consumers (e.g. renamed/deleted field, new required parameter, type alteration, route removal).\n")
                .append("4. Generate precise patch suggestions with semantic and contract classification.\n\n");

        if (contractFacts != null && !contractFacts.isEmpty()) {
            prompt.append("### Verified API Contract Differences:\n")
                    .append("A deterministic comparison of the code's endpoints with this specification found the following. ")
                    .append("Treat them as facts: produce a suggestion for each one that fixes the specification text.\n");
            contractFacts.forEach(fact -> prompt.append("- ").append(fact).append('\n'));
            prompt.append('\n');
        }

        return prompt
                .append("### Code Diff:\n").append(diff).append("\n\n")
                .append("### Target File (").append(filePath).append("):\n").append(docContent).append("\n\n")
                .append("### Response Rules:\n")
                .append("Return one suggestion per outdated passage, with these fields:\n")
                .append("- filePath: \"").append(filePath).append("\"\n")
                .append("- driftType: one of API_CONTRACT_BREAKING_CHANGE, SCHEMA_DRIFT, API_SIGNATURE_MISMATCH, CONFIG_PROPERTY_CHANGED, CLI_USAGE_CHANGED, BEHAVIORAL_LOGIC_DRIFT, SAMPLE_CODE_BROKEN\n")
                .append("- severity: one of CRITICAL, HIGH, MEDIUM, LOW\n")
                .append("- confidenceScore: 0.0 to 1.0\n")
                .append("- impactedSymbol: e.g. \"POST /api/v1/orders\" or \"OrderRequest.userId\"\n")
                .append("- isBreakingChange: true or false\n")
                .append("- httpMethod: GET, POST, PUT, DELETE or PATCH when the drift concerns an endpoint, otherwise empty\n")
                .append("- endpointPath: e.g. \"/api/users\" when the drift concerns an endpoint, otherwise empty\n")
                .append("- schemaFormat: one of OPENAPI_YAML, SWAGGER_JSON, MARKDOWN_TABLE, GENERAL_PROSE\n")
                .append("- oldText: the outdated text, copied verbatim from the target file so it can be found and replaced exactly\n")
                .append("- suggestedText: the updated text that replaces oldText\n")
                .append("- reason: what changed in the code or API contract and why this documentation update is needed\n")
                .append("If no semantic or contract drift is detected, return no suggestions.")
                .toString();
    }
}
