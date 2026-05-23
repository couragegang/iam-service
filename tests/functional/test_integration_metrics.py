"""Regress: метрика исходящих HTTP после реального трафика (integration.http)."""

import os

import pytest
import requests

from lib.chat_assert import expected_chat_status, parse_chat_response
from lib.metrics_assert import assert_integration_metrics_optional, metrics_url

pytestmark = pytest.mark.regress

SERVICE_ID = os.environ.get("SERVICE_ID", "iam")

# BC, где regress гарантированно дергает исходящий JDK HttpClient
_OUTBOUND_HTTP_REGRESS = frozenset({"iam", "bff", "ai"})


@pytest.mark.skipif(
    SERVICE_ID not in _OUTBOUND_HTTP_REGRESS,
    reason="no guaranteed outbound HTTP in this BC regress suite",
)
def test_integration_http_after_traffic(require_compose, api_session):
    if SERVICE_ID == "iam":
        api_session.iam_me()
    elif SERVICE_ID == "bff":
        api_session.bff_me()
    else:
        body = parse_chat_response(api_session.bff_chat({"message": "metrics regress"}))
        assert body["status"] == expected_chat_status()

    r = requests.get(metrics_url(SERVICE_ID), timeout=15)
    assert r.status_code == 200, r.text[:300]
    assert_integration_metrics_optional(r.text, SERVICE_ID)
