package com.bitvelocity.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Inventory Spike Test
 * 
 * Tests inventory service behavior under sudden load spikes.
 * Simulates scenarios like flash sales where inventory checks spike suddenly.
 * 
 * Load Profile: Immediate spike to 200 users, sustain for 2 minutes, then drop
 */
public class InventorySpikeTest extends Simulation {

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Spike Test");

    // Feeder for product IDs
    Iterator<Map<String, Object>> productFeeder = 
        Stream.continually(() -> Map.<String, Object>of(
            "productId", "PROD-" + ThreadLocalRandom.current().nextInt(100), // Limited set for cache testing
            "quantity", ThreadLocalRandom.current().nextInt(1, 5),
            "correlationId", UUID.randomUUID().toString()
        )).iterator();

    // Scenario: Check inventory availability
    ScenarioBuilder inventoryCheckScenario = scenario("Inventory Check")
        .feed(productFeeder)
        .exec(
            http("Check Stock")
                .get("/api/v1/inventory/#{productId}")
                .header("Correlation-Id", "#{correlationId}")
                .check(status().is(200))
                .check(jsonPath("$.available").saveAs("stockLevel"))
        )
        .pause(Duration.ofMillis(100), Duration.ofMillis(500));

    // Scenario: Reserve inventory (more intensive)
    ScenarioBuilder inventoryReserveScenario = scenario("Inventory Reserve")
        .feed(productFeeder)
        .exec(
            http("Reserve Stock")
                .post("/api/v1/inventory/reserve")
                .header("Correlation-Id", "#{correlationId}")
                .body(StringBody("{\"productId\": \"#{productId}\", \"quantity\": #{quantity}}"))
                .check(status().in(200, 201, 409)) // 409 = out of stock acceptable
        )
        .pause(Duration.ofMillis(200), Duration.ofMillis(800));

    // Load Profile: Spike pattern
    {
        setUp(
            // 80% reads, 20% writes (realistic ratio)
            inventoryCheckScenario.injectOpen(
                nothingFor(Duration.ofSeconds(5)),          // Initial calm
                atOnceUsers(160),                           // Sudden spike
                rampUsers(40).during(Duration.ofSeconds(10)) // Slight ramp
            ),
            inventoryReserveScenario.injectOpen(
                nothingFor(Duration.ofSeconds(5)),
                atOnceUsers(40),
                rampUsers(10).during(Duration.ofSeconds(10))
            )
        ).protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95.0).lt(100),  // p95 < 100ms even under spike
            global().failedRequests().percent().lt(5.0)        // < 5% failures (some 409s expected)
        );
    }

    // Helper method to create continuous stream
    private static <T> java.util.stream.Stream<T> continually(Supplier<T> supplier) {
        return java.util.stream.Stream.generate(supplier);
    }
}
