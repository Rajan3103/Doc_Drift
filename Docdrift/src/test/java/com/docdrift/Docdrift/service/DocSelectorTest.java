package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocSelectorTest {

    @Test
    void picksDocsInPriorityOrderAndSkipsHistoricalAndVendoredFiles() {
        List<String> paths = List.of(
                "src/Main.java", "frontend/README.md", "docs/guide.md", "README.md", "api/openapi.yaml",
                "CHANGELOG.md", "LICENSE.md", "node_modules/lib/README.md", ".github/pull_request_template.md",
                "CONTRIBUTING.md");

        assertEquals(List.of("api/openapi.yaml", "README.md", "docs/guide.md", "CONTRIBUTING.md", "frontend/README.md"),
                DocSelector.candidates(paths, 10));
        assertEquals(List.of("api/openapi.yaml", "README.md"), DocSelector.candidates(paths, 2));
    }

    @Test
    void keywordsComeFromRemovedLinesAndChangedSourceFiles() {
        String diff = "--- a/src/OrderController.java\n"
                + "-    @GetMapping(\"/api/orders\")\n"
                + "-    public List<Order> listOrders() {\n"
                + "+    public List<Order> fetchOrders() {\n";

        Set<String> keywords = DocSelector.keywords(diff, List.of("src/OrderController.java", "README.md"));

        assertTrue(keywords.contains("OrderController"));
        assertTrue(keywords.contains("/api/orders"));
        assertTrue(keywords.contains("listOrders"));
        assertFalse(keywords.contains("fetchOrders"), "added lines are not keywords");
        assertFalse(keywords.contains("public"), "language keywords are ignored");
        assertFalse(keywords.contains("README"), "changed docs are not keywords");
    }

    @Test
    void otherDocsAreOnlyRelevantWhenTheyMentionAChange() {
        Set<String> keywords = Set.of("listOrders");
        assertTrue(DocSelector.isRelevant("README.md", "nothing related", keywords));
        assertTrue(DocSelector.isRelevant("openapi.yaml", "nothing related", keywords));
        assertTrue(DocSelector.isRelevant("docs/api.md", "Call listOrders() to ...", keywords));
        assertFalse(DocSelector.isRelevant("docs/deploy.md", "How to deploy", keywords));
    }
}
