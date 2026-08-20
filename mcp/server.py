#!/usr/bin/env python3
"""PiFlash remote MCP — GitHub Actions + Play Developer API."""

from __future__ import annotations

import json
import os
import tempfile
from typing import Any

import httpx
import uvicorn
from mcp.server.fastmcp import FastMCP
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse

OWNER = os.environ.get("GITHUB_OWNER", "janisxyz")
REPO = os.environ.get("GITHUB_REPO", "PiFlash")
PACKAGE = os.environ.get("PLAY_PACKAGE", "piflash.shizoghost.com")
WORKFLOW = os.environ.get("RELEASE_WORKFLOW", "release-aab.yml")
MCP_TOKEN = os.environ.get("MCP_TOKEN", "")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
PLAY_SA_JSON = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "")

mcp = FastMCP("piflash", stateless_http=True, json_response=True)


class BearerAuth(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if request.url.path in ("/health", "/"):
            return await call_next(request)
        if not MCP_TOKEN:
            return await call_next(request)
        auth = request.headers.get("authorization", "")
        if auth != f"Bearer {MCP_TOKEN}":
            return JSONResponse({"error": "unauthorized"}, status_code=401)
        return await call_next(request)


def _gh_headers() -> dict[str, str]:
    if not GITHUB_TOKEN:
        raise RuntimeError("GITHUB_TOKEN is not set on the MCP host")
    return {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def _play_service():
    if not PLAY_SA_JSON:
        raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON is not set on the MCP host")
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    info = json.loads(PLAY_SA_JSON)
    creds = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


@mcp.tool()
def trigger_release(track: str = "internal") -> str:
    """Dispatch GitHub Actions Release AAB. Uploads to Play if PLAY_SERVICE_ACCOUNT_JSON is set in GitHub secrets. track: internal or production."""
    if track not in ("internal", "production"):
        return "track must be internal or production"
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/actions/workflows/{WORKFLOW}/dispatches"
    r = httpx.post(
        url,
        headers=_gh_headers(),
        json={"ref": "main", "inputs": {"play_track": track}},
        timeout=30.0,
    )
    if r.status_code not in (204, 200):
        return f"GitHub dispatch failed {r.status_code}: {r.text}"
    return f"Triggered {WORKFLOW} on main → Play track {track}"


@mcp.tool()
def list_recent_runs(limit: int = 5) -> str:
    """List recent Release AAB workflow runs."""
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/actions/workflows/{WORKFLOW}/runs"
    r = httpx.get(url, headers=_gh_headers(), params={"per_page": limit}, timeout=30.0)
    r.raise_for_status()
    runs = r.json().get("workflow_runs", [])
    lines = []
    for run in runs:
        lines.append(
            f"#{run['run_number']} {run['status']}/{run.get('conclusion') or '-'} "
            f"{run['html_url']}"
        )
    return "\n".join(lines) or "No runs"


@mcp.tool()
def play_tracks() -> str:
    """List Google Play tracks and latest releases for piflash.shizoghost.com."""
    svc = _play_service()
    edit = svc.edits().insert(packageName=PACKAGE, body={}).execute()
    edit_id = edit["id"]
    try:
        tracks = svc.edits().tracks().list(packageName=PACKAGE, editId=edit_id).execute()
    finally:
        svc.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
    out: list[dict[str, Any]] = []
    for t in tracks.get("tracks", []):
        rels = t.get("releases") or []
        latest = rels[0] if rels else {}
        out.append(
            {
                "track": t.get("track"),
                "status": latest.get("status"),
                "name": latest.get("name"),
                "versionCodes": latest.get("versionCodes"),
            }
        )
    return json.dumps(out, indent=2)


@mcp.tool()
def health() -> str:
    """Check which backends this MCP host can reach."""
    return json.dumps(
        {
            "github": bool(GITHUB_TOKEN),
            "play": bool(PLAY_SA_JSON),
            "package": PACKAGE,
            "repo": f"{OWNER}/{REPO}",
        }
    )


def main() -> None:
    app = mcp.streamable_http_app()
    app.add_middleware(BearerAuth)

    async def health_http(request: Request):
        return JSONResponse({"ok": True, "server": "piflash-mcp"})

    app.add_route("/health", health_http, methods=["GET"])
    port = int(os.environ.get("PORT", "8080"))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")


if __name__ == "__main__":
    main()
