import http.client
import json
import socket
import threading
import time
import unittest

import uvicorn

from main import app


VALID_PROFILE = {
    "returnOnAssets": 8,
    "profitMargin": 12,
    "fixedAssetTurnover": 1.5,
    "debtToTotalCapitalPercent": 40,
    "interestCoverage": 5,
    "totalDebtToEbitda": 2.5,
}


def reject_json_constant(value):
    raise ValueError(f"Invalid JSON constant: {value}")


class PredictionApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        listener = socket.socket()
        cls.addClassCleanup(listener.close)
        listener.bind(("127.0.0.1", 0))
        cls.port = listener.getsockname()[1]
        server = uvicorn.Server(uvicorn.Config(app, log_level="warning", proxy_headers=False))
        thread = threading.Thread(target=server.run, kwargs={"sockets": [listener]}, daemon=True)
        thread.start()

        def stop_server():
            server.should_exit = True
            thread.join(timeout=5)

        cls.addClassCleanup(stop_server)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if server.started:
                return
            if not thread.is_alive():
                break
            time.sleep(0.05)
        raise RuntimeError("FastAPI did not start")

    def request(self, method, path, body=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request(method, path, body, {"Content-Type": "application/json"})
            response = connection.getresponse()
            return response.status, json.loads(response.read(), parse_constant=reject_json_constant)
        finally:
            connection.close()

    def test_health(self):
        self.assertEqual(self.request("GET", "/health"), (200, {"status": "UP", "modelLoaded": True}))

    def test_valid_prediction_matches_baseline(self):
        status, result = self.request("POST", "/predict", json.dumps(VALID_PROFILE))
        self.assertEqual(status, 200)
        self.assertEqual(result, {
            "probability": 0.02346294345952352,
            "riskLevel": "LOW_RISK",
            "modelVersion": "financial-risk-model-sklearn-1.6.1",
        })

    def test_non_finite_numbers_return_json_422(self):
        for field in VALID_PROFILE:
            for value in ("1e999", "-1e999", "Infinity", "-Infinity", "NaN"):
                with self.subTest(field=field, value=value):
                    body = json.dumps({**VALID_PROFILE, field: "VALUE"}).replace('"VALUE"', value)
                    status, result = self.request("POST", "/predict", body)
                    self.assertEqual(status, 422)
                    self.assertEqual(result["detail"][0]["loc"], ["body", field])

    def test_missing_ratios_return_422(self):
        for field in VALID_PROFILE:
            with self.subTest(field=field):
                body = json.dumps({key: value for key, value in VALID_PROFILE.items() if key != field})
                self.assertEqual(self.request("POST", "/predict", body)[0], 422)

    def test_nonnumeric_ratio_returns_422(self):
        body = json.dumps({**VALID_PROFILE, "profitMargin": "invalid"})
        self.assertEqual(self.request("POST", "/predict", body)[0], 422)
