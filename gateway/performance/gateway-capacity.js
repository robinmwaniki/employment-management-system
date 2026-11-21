import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "http://localhost:8081").replace(/\/$/, "");
const token = __ENV.EMS_TOKEN;
const environment = (__ENV.LOAD_TEST_ENV || "").toLowerCase();
const acknowledgement = __ENV.LOAD_TEST_ACK;
const targetVus = Number(__ENV.TARGET_VUS || 25);
const rampDuration = __ENV.RAMP_DURATION || "2m";
const holdDuration = __ENV.HOLD_DURATION || "5m";
const drainDuration = __ENV.DRAIN_DURATION || "2m";

if (!token) {
  throw new Error("Set EMS_TOKEN to a short-lived synthetic employee JWT.");
}

if (!["local", "staging"].includes(environment) || acknowledgement !== "I_CONFIRM_NON_PRODUCTION") {
  throw new Error("Capacity tests require LOAD_TEST_ENV=local or staging and LOAD_TEST_ACK=I_CONFIRM_NON_PRODUCTION.");
}

if (/\.onrender\.com(?::\d+)?(?:\/|$)/i.test(baseUrl) || /production|prod-api/i.test(baseUrl)) {
  throw new Error("Capacity tests are blocked against public Render/production hosts. Use isolated local or staging infrastructure.");
}

if (!Number.isInteger(targetVus) || targetVus < 1) {
  throw new Error("TARGET_VUS must be a positive integer.");
}

if (targetVus > 1000 && __ENV.DISTRIBUTED_LOAD_TEST !== "true") {
  throw new Error("A single k6 generator is capped at 1000 VUs. Use a distributed runner and set DISTRIBUTED_LOAD_TEST=true for higher targets.");
}

export const options = {
  scenarios: {
    employee_read_mix: {
      executor: "ramping-vus",
      startVUs: 1,
      stages: [
        { duration: rampDuration, target: targetVus },
        { duration: holdDuration, target: targetVus },
        { duration: drainDuration, target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
  },
};

const readEndpoints = [
  "/api/me/profile",
  "/api/me/leave?page=0&size=10",
  "/api/me/attendance?page=0&size=20",
  "/api/me/payroll?page=0&size=12",
  "/api/me/notifications/unread-count",
];

export default function () {
  const endpoint = readEndpoints[Math.floor(Math.random() * readEndpoints.length)];
  const response = http.get(`${baseUrl}${endpoint}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
    },
    tags: {
      scenario: "employee-self-service-read",
    },
  });

  check(response, {
    "self-service request succeeds": (result) => result.status >= 200 && result.status < 300,
  });

  sleep(0.1);
}
