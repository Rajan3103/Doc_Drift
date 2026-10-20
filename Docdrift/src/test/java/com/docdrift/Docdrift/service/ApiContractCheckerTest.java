package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.service.ApiContractChecker.Endpoint;
import com.docdrift.Docdrift.service.ApiContractChecker.Finding;
import com.docdrift.Docdrift.service.ApiContractChecker.FindingKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiContractCheckerTest {

    private static final String CONTROLLER = """
            package demo;

            // @GetMapping("/commented-out") must be ignored
            @RestController
            @RequestMapping("/api/orders")
            public class OrderController {

                @GetMapping
                public List<Order> list() { return List.of(); }

                @GetMapping(value = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
                public Order get(@PathVariable Long orderId) { return null; }

                @PostMapping(path = {"", "/bulk"}, consumes = "application/json")
                public void create() { }

                @RequestMapping(value = "/{id}/cancel", method = RequestMethod.PUT)
                public void cancel() { }

                /* @DeleteMapping("/old") */
            }
            """;

    @Test
    void extractsSpringEndpointsWithClassPrefix() {
        assertThat(ApiContractChecker.endpointsInSpringSource(CONTROLLER)).containsExactlyInAnyOrder(
                new Endpoint("GET", "/api/orders"),
                new Endpoint("GET", "/api/orders/{orderId}"),
                new Endpoint("POST", "/api/orders"),
                new Endpoint("POST", "/api/orders/bulk"),
                new Endpoint("PUT", "/api/orders/{id}/cancel"));
    }

    @Test
    void readsOpenApiAndSwaggerPathsWithPrefix() {
        String openApi = """
                openapi: 3.0.0
                servers:
                  - url: https://api.example.com/api
                paths:
                  /orders:
                    get: {}
                    post: {}
                  /orders/{id}:
                    parameters: []
                    delete: {}
                """;
        assertThat(ApiContractChecker.endpointsInSpec(openApi)).containsExactlyInAnyOrder(
                new Endpoint("GET", "/api/orders"),
                new Endpoint("POST", "/api/orders"),
                new Endpoint("DELETE", "/api/orders/{id}"));

        String swaggerJson = """
                {"swagger": "2.0", "basePath": "/v1", "paths": {"/users/": {"get": {}}}}""";
        assertThat(ApiContractChecker.endpointsInSpec(swaggerJson)).containsExactly(new Endpoint("GET", "/v1/users"));

        assertThatThrownBy(() -> ApiContractChecker.endpointsInSpec("# just markdown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathVariableNamesDoNotMatter() {
        assertThat(new Endpoint("GET", "/orders/{id}")).isEqualTo(new Endpoint("GET", "/orders/{orderId}"));
        assertThat(new Endpoint("GET", "/orders/{id}")).isNotEqualTo(new Endpoint("DELETE", "/orders/{id}"));
    }

    @Test
    void reportsRemovedButDocumentedAndAddedButUndocumented() {
        Map<String, Set<Endpoint>> before = Map.of("OrderController.java",
                Set.of(new Endpoint("GET", "/api/orders"), new Endpoint("GET", "/api/orders/{id}")));
        Map<String, Set<Endpoint>> after = Map.of("OrderController.java",
                Set.of(new Endpoint("GET", "/api/orders/{orderId}"), new Endpoint("POST", "/api/v2/orders")));
        Set<Endpoint> spec = Set.of(new Endpoint("GET", "/api/orders"), new Endpoint("GET", "/api/orders/{id}"));

        List<Finding> findings = ApiContractChecker.compare(before, after, spec);

        assertThat(findings).containsExactlyInAnyOrder(
                new Finding(FindingKind.REMOVED_BUT_DOCUMENTED, new Endpoint("GET", "/api/orders"), "OrderController.java"),
                new Finding(FindingKind.ADDED_BUT_UNDOCUMENTED, new Endpoint("POST", "/api/v2/orders"), "OrderController.java"));
        assertThat(findings.getFirst().describe("openapi.yaml"))
                .isEqualTo("GET /api/orders was removed from OrderController.java but is still documented in openapi.yaml.");
    }

    @Test
    void endpointMovedBetweenControllersIsNotAFinding() {
        Endpoint moved = new Endpoint("GET", "/api/health");
        assertThat(ApiContractChecker.compare(Map.of("A.java", Set.of(moved)), Map.of("B.java", Set.of(moved)), Set.of(moved)))
                .isEmpty();
    }

    @Test
    void recognisesLlmSuggestionsCoveringAFinding() {
        Endpoint endpoint = new Endpoint("GET", "/api/orders/{id}");
        assertThat(ApiContractChecker.coveredBy(endpoint, "get", "/orders/{orderId}")).isTrue();
        assertThat(ApiContractChecker.coveredBy(endpoint, "POST", "/api/orders/{id}")).isFalse();
        assertThat(ApiContractChecker.coveredBy(endpoint, null, null)).isFalse();
    }
}
