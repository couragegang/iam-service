"""Smoke: service health on running compose stack."""

import os

import pytest
import requests

from lib.config import SERVICES_HEALTH

pytestmark = pytest.mark.smoke

SERVICE_ID = os.environ.get("SERVICE_ID", "iam")


def test_service_health(require_compose):
    url = SERVICES_HEALTH[SERVICE_ID]
    r = requests.get(url, timeout=15)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body.get("status") == "UP" or "UP" in str(body)
