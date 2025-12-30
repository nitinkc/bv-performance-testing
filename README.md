# BitVelocity Performance Testing

This module provides load testing, performance benchmarking, and capacity planning tooling for the BitVelocity platform.

## Structure

```
bv-performance-testing/
├── gatling-tests/          # Gatling-based load tests
├── k6-scripts/             # k6 performance scripts
├── performance-baselines/  # SLI/SLO targets and benchmark results
├── jmeter-tests/           # JMeter test plans (optional)
└── reports/                # Generated test reports
```

## Goals

- Establish performance baselines for all critical user flows
- Implement performance regression testing in CI/CD
- Practice capacity planning and scalability analysis
- Learn latency percentile analysis (p50, p95, p99)
- Understand system behavior under load

## Quick Start

### Gatling Tests

```bash
cd gatling-tests
./gradlew gatlingRun
```

### k6 Scripts

```bash
cd k6-scripts
k6 run api-smoke-test.js
```

## Performance Targets (Initial Baselines)

| Endpoint/Flow | p95 Latency | p99 Latency | Throughput |
|---------------|-------------|-------------|------------|
| POST /orders | < 200ms | < 500ms | 100 req/s |
| GET /products/{id} | < 50ms | < 100ms | 500 req/s |
| gRPC ReserveStock | < 100ms | < 200ms | 200 req/s |
| WebSocket message fan-out | < 150ms | < 300ms | 1000 msg/s |

## Test Scenarios

### 1. Order Creation Flow (Gatling)
- Simulates realistic e-commerce checkout
- Includes: product lookup → cart add → order creation → payment
- Load profile: ramp from 10 to 100 users over 5 minutes

### 2. API Smoke Test (k6)
- Quick validation of all REST endpoints
- 10 VUs for 30 seconds
- Used in CI pipeline

### 3. Spike Test (k6)
- Tests system behavior under sudden load increase
- Useful for validating circuit breakers and rate limiting

### 4. Stress Test (Gatling)
- Identifies system breaking points
- Incrementally increases load until failures occur

## Integration with CI/CD

Performance smoke tests run on every PR to `main` branch. Full load tests run nightly.

See `.github/workflows/performance-smoke.yml` for CI integration.

## Metrics Collection

All tests automatically collect:
- Response time distributions (p50, p95, p99)
- Error rates
- Throughput (requests/second)
- Resource utilization (CPU, memory, network)

Results are stored in `reports/` and can be visualized in Grafana.

## Learning Resources

- [Gatling Documentation](https://gatling.io/docs/)
- [k6 Documentation](https://k6.io/docs/)
- [Performance Testing Best Practices](../../BitVelocity-Docs/docs/03-DEVELOPMENT/performance-testing-guide.md)
