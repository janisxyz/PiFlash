# PiFlash MCP (Grok connector)

Remote MCP so Grok can trigger GitHub Actions and read Play tracks.

Grok already has the **GitHub** connector. This extra server is only needed if you want Play Console tools in chat, or a dedicated `trigger_release` tool.

## Tools

| Tool | What it does |
|------|----------------|
| `trigger_release` | `workflow_dispatch` **Release AAB** (`internal` or `production`) |
| `list_recent_runs` | Last GitHub Actions runs |
| `play_tracks` | Play Internal/Closed/Production releases |
| `health` | Which credentials are configured |

## Host env

| Env | Required |
|-----|----------|
| `MCP_TOKEN` | Bearer token Grok sends |
| `GITHUB_TOKEN` | PAT with `repo` + `workflow` |
| `PLAY_SERVICE_ACCOUNT_JSON` | same JSON as the GitHub secret |
| `GITHUB_OWNER` | default `janisxyz` |
| `GITHUB_REPO` | default `PiFlash` |
| `PLAY_PACKAGE` | default `piflash.shizoghost.com` |

## Deploy (Fly example)

```bash
cd mcp
fly launch --name piflash-mcp --region fra --no-deploy
fly secrets set MCP_TOKEN=... GITHUB_TOKEN=... PLAY_SERVICE_ACCOUNT_JSON="$(cat play.json)"
fly deploy
```

Public URL will look like `https://piflash-mcp.fly.dev/mcp`.

## Connect in Grok

1. Open https://grok.com/connectors
2. **New Connector → Custom**
3. Server URL: `https://<your-host>/mcp`
4. Auth: Bearer `MCP_TOKEN`
5. New chat → ask “trigger a PiFlash internal release”

Do **not** expose this URL without `MCP_TOKEN`. `trigger_release(production)` ships to the public store.
