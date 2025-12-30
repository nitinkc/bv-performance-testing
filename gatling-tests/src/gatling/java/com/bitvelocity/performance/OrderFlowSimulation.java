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
 * Order Flow Simulation
 * 
 * Simulates realistic e-commerce order creation flow:
 * 1. Browse products
 * 2. Add to cart
 * 3. Create order
 * 4. Process payment
 * 
 * Load Profile: Ramp from 10 to 100 users over 5 minutes
 */
public class OrderFlowSimulation extends Simulation {

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Load Test");

    // Custom headers with correlation and idempotency
    Map<CharSequence, String> headers = Map.of(
        "Authorization", "Bearer #{authToken}",
        "Correlation-Id", "#{correlationId}",
        "Idempotency-Key", "#{idempotencyKey}"
    );

    // Feeder for test data
    Iterator<Map<String, Object>> productFeeder = 
        Stream.continually(() -> Map.<String, Object>of(
            "productId", "PROD-" + ThreadLocalRandom.current().nextInt(1000),
            "quantity", ThreadLocalRandom.current().nextInt(1, 6),
            "correlationId", UUID.randomUUID().toString(),
            "idempotencyKey", UUID.randomUUID().toString()
        )).iterator();

    // Scenario Definition
    ScenarioBuilder orderFlowScenario = scenario("Order Creation Flow")
        .feed(productFeeder)
        
        // Step 1: Get product details
        .exec(
            http("Get Product")
                .get("/api/v1/products/#{productId}")
                .check(status().is(200))
                .check(jsonPath("$.price").saveAs("productPrice"))
        )
        .pause(Duration.ofSeconds(1), Duration.ofSeconds(3)) // User think time
        
        // Step 2: Add to cart
        .exec(
            http("Add to Cart")
                .post("/api/v1/cart/items")
                .headers(headers)
                .body(StringBody("{\"productId\": \"#{productId}\", \"quantity\": #{quantity}}"))
                .check(status().is(201))
                .check(jsonPath("$.cartId").saveAs("cartId"))
        )
        .pause(Duration.ofSeconds(2), Duration.ofSeconds(5))
        
        // Step 3: Create order
        .exec(
            http("Create Order")
                .post("/api/v1/orders")
                .headers(headers)
                .body(StringBody("{\"cartId\": \"#{cartId}\", \"userId\": \"USER-TEST\"}"))
                .check(status().is(201))
                .check(jsonPath("$.orderId").saveAs("orderId"))
        )
        .pause(Duration.ofSeconds(1), Duration.ofSeconds(2))
        
        // Step 4: Process payment
        .exec(
            http("Process Payment")
                .post("/api/v1/orders/#{orderId}/pay")
                .headers(headers)
                .body(StringBody("{\"paymentMethod\": \"CREDIT_CARD\", \"amount\": #{productPrice}}"))
                .check(status().in(200, 202))
        );

    // Load Profile and Assertions
    {
        setUp(
            orderFlowScenario.injectOpen(
                rampUsers(100).during(Duration.ofMinutes(5))
            ).protocols(httpProtocol)
        ).assertions(
            global().responseTime().percentile(95.0).lt(200),  // p95 < 200ms
            global().successfulRequests().percent().gt(95.0)    // 95% success rate
        );
    }

    // Helper method to create continuous stream
    private static <T> java.util.stream.Stream<T> continually(Supplier<T> supplier) {
        return java.util.stream.Stream.generate(supplier);
    }
}
