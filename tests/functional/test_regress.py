"""IAM regress: register, me, organizations."""

import pytest

from lib.http_client import ApiSession

pytestmark = pytest.mark.regress


def test_register_me_and_orgs(require_compose):
    s = ApiSession()
    s.register_with_org()
    s.resolve_org_and_workspace()
    me = s.iam_me()
    assert me.get("user", {}).get("id") == s.user_id
    assert me.get("user", {}).get("email")
