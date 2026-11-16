/**
 * k6 Load Test Script for Upcraft Stay Backend (Milestone 7).
 *
 * Design Targets (ASSUMPTIONS — NOT pre-verified guarantees):
 *   - 20 average RPS steady-state
 *   - 200 peak RPS burst
 *   - p95 latency < 120ms for core read/search endpoints
 *
 * Usage (explicitly run by operator against isolated staging/local instance):
 *   k6 run -e BASE_URL=http://localhost:8080 -e TEST_EMAIL=... -e TEST_PASSWORD=... scripts/load-test.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const TEST_EMAIL = __ENV.TEST_EMAIL || "owner@upcraft.test";
const TEST_PASSWORD = __ENV.TEST_PASSWORD || "ChangeMe123!";

export const options = {
  scenarios: {
    average_load: {
      executor: "constant-arrival-rate",
      rate: 20,
      timeUnit: "1s",
      duration: "1m",
      preAllocatedVUs: 25,
      maxVUs: 100,
    },
    peak_burst: {
      executor: "constant-arrival-rate",
      startTime: "1m10s",
      rate: 200,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  thresholds: {
    "http_req_duration{kind:read}": ["p(95)<120"],
    http_req_failed: ["rate<0.02"],
  },
};

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  if (res.status !== 200) {
    return { token: "" };
  }
  const body = JSON.parse(res.body);
  return { token: body?.data?.accessToken || "" };
}

export default function (data) {
  const healthRes = http.get(`${BASE_URL}/actuator/health`, { tags: { kind: "read" } });
  check(healthRes, { "health 200": (r) => r.status === 200 });

  if (data.token) {
    const headers = { Authorization: `Bearer ${data.token}` };
    const searchRes = http.get(`${BASE_URL}/api/v1/search?q=101`, {
      headers,
      tags: { kind: "read" },
    });
    check(searchRes, { "search 200": (r) => r.status === 200 });

    const auditRes = http.get(`${BASE_URL}/api/v1/audit-logs?page=0&size=20`, {
      headers,
      tags: { kind: "read" },
    });
    check(auditRes, { "audit 200 or 429": (r) => r.status === 200 || r.status === 429 });

    const dashboardRes = http.get(`${BASE_URL}/api/v1/dashboard/owner`, {
      headers,
      tags: { kind: "read" },
    });
    check(dashboardRes, { "dashboard 200": (r) => r.status === 200 });
  }

  sleep(0.1);
}

