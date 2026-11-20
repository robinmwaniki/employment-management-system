import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "http://localhost:8081").replace(/\/$/, "");
const token = __ENV.EMS_TOKEN;

if (!token) {
  throw new Error("Set EMS_TOKEN to a short-lived test JWT before running the gateway smoke test.");
}

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || "30s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000"],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/employees`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
    },
    tags: {
      scenario: "gateway-authenticated-employees-read",
    },
  });

  check(response, {
    "gateway/backend returns a successful response": (result) =>
      result.status >= 200 && result.status < 300,
  });

  sleep(0.1);
}
