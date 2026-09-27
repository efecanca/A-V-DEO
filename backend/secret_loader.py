"""Backend-only secret lookup. Values are never logged or returned by the API."""

from __future__ import annotations

import os


def load_secret(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if value:
        return value

    try:
        from kaggle_secrets import UserSecretsClient

        value = (UserSecretsClient().get_secret(name) or "").strip()
        if value:
            os.environ[name] = value
            return value
    except Exception:
        pass

    try:
        from google.colab import userdata

        value = (userdata.get(name) or "").strip()
        if value:
            os.environ[name] = value
            return value
    except Exception:
        pass
    return ""

