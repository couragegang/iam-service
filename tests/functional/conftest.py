"""Fixtures for per-service functional tests (uses platform/tests/e2e/lib on PYTHONPATH)."""

import os
import sys
import time
from pathlib import Path

import pytest
import requests

SERVICE_ID = os.environ.get("SERVICE_ID", "iam")


def _platform_e2e_root() -> Path:
    pr = os.environ.get("PLATFORM_ROOT")
    if pr:
        return Path(pr) / "tests" / "e2e"
    return Path(__file__).resolve().parents[4] / "platform" / "tests" / "e2e"


_ROOT = _platform_e2e_root()
if _ROOT.is_dir() and str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from lib.config import SERVICES_HEALTH  # noqa: E402

try:
    from lib.http_client import ApiSession  # noqa: E402
except ImportError as e:
    ApiSession = None  # type: ignore[misc, assignment]
    _import_error = e
else:
    _import_error = None


@pytest.fixture(scope="session")
def require_compose():
    """Fail fast if platform stack is not healthy."""
    deadline = time.time() + 120
    last_errors: list[str] = []
    while time.time() < deadline:
        last_errors.clear()
        for name, url in SERVICES_HEALTH.items():
            try:
                r = requests.get(url, timeout=5)
                if r.status_code != 200:
                    last_errors.append(f"{name}: HTTP {r.status_code}")
            except requests.RequestException as e:
                last_errors.append(f"{name}: {e}")
        if not last_errors:
            return
        time.sleep(3)
    pytest.fail("Services not healthy:\n" + "\n".join(last_errors))


@pytest.fixture
def api_session(require_compose):
    if ApiSession is None:
        pytest.fail(f"lib.http_client not available: {_import_error}")
    s = ApiSession()
    s.register_with_org()
    s.resolve_org_and_workspace()
    return s
