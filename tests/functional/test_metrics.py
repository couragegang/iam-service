"""Smoke: Prometheus /metrics и 4 золотых сигнала (traffic, latency, errors, saturation)."""

import os

import pytest
import requests

from lib.metrics_assert import assert_prometheus_golden_signals, metrics_url

pytestmark = pytest.mark.smoke

SERVICE_ID = os.environ.get("SERVICE_ID", "iam")


def test_prometheus_golden_signals(require_compose):
    url = metrics_url(SERVICE_ID)
    r = requests.get(url, timeout=15)
    assert r.status_code == 200, r.text
    assert "text/plain" in r.headers.get("Content-Type", "")
    assert_prometheus_golden_signals(r.text, SERVICE_ID)
