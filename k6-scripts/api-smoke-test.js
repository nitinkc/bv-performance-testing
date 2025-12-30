import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

/**
 * API Smoke Test
 * 
 * Quick validation of all critical API endpoints.
 * Used in CI pipeline for performance regression detection.
 * 
 * Run: k6 run api-smoke-test.js
 */

// Custom metrics
const errorRate = new Rate('errors');

// Test configuration
export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% of requests must complete below 200ms
    errors: ['rate<0.1'],              // Error rate must be below 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const correlationId = generateUUID();
  const headers = {
    'Content-Type': 'application/json',
    'Correlation-Id': correlationId,
  };

  // Test 1: Health check
  let response = http.get(`${BASE_URL}/actuator/health`, { headers });
  check(response, {
    'health check status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  sleep(0.5);

  // Test 2: Get products
  response = http.get(`${BASE_URL}/api/v1/products?page=0&size=10`, { headers });
  check(response, {
    'products list status is 200': (r) => r.status === 200,
    'products response time < 100ms': (r) => r.timings.duration < 100,
  }) || errorRate.add(1);
  sleep(0.5);

  // Test 3: Get specific product
  const productId = 'PROD-1';
  response = http.get(`${BASE_URL}/api/v1/products/${productId}`, { headers });
  check(response, {
    'product detail status is 200': (r) => r.status === 200,
    'product has required fields': (r) => {
      const body = JSON.parse(r.body);
      return body.productId && body.name && body.price;
    },
  }) || errorRate.add(1);
  sleep(0.5);

  // Test 4: Check inventory
  response = http.get(`${BASE_URL}/api/v1/inventory/${productId}`, { headers });
  check(response, {
    'inventory check status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  sleep(1);
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'reports/summary.json': JSON.stringify(data),
  };
}
