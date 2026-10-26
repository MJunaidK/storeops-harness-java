# Deployment — StoreOps (local Docker)

## Target

**Local Docker** (Docker Desktop 4.27.2, Engine 25.0.3, Windows 11 host), as allowed by PDF §3.4.
Cloud deployment was not done; the decisions it would require are listed at the end.

## Steps

1. **Run the quality gates first.** The image build skips them (see "Notes"), so they run before it:
   ```powershell
   .\mvnw.cmd verify      # BUILD SUCCESS, 114 tests, Checkstyle/SpotBugs/JaCoCo/ArchUnit green
   ```
2. **Build and start** (multi-stage `Dockerfile`: JDK 21 build stage, JRE 21 runtime, non-root user `storeops`):
   ```powershell
   docker compose up --build -d
   docker ps              # storeops:latest, Up, 0.0.0.0:8080->8080/tcp
   ```
3. **Smoke test:** `curl.exe -s -H "X-User-Id: u-manager" http://localhost:8080/api/activities` → `[]`.

## Demonstration of the harness-built feature

Full transcript: [`docs/deployment/demo-transcript.txt`](docs/deployment/demo-transcript.txt) ·
screenshots: [`demo-1.png`](docs/deployment/demo-1.png) (container + bulk update),
[`demo-2.png`](docs/deployment/demo-2.png) (audit, access rule, validation)

Setup: task A (owner `u-manager`, assignee `u-associate`) and task B (owner and assignee `u-lead`), both TODO.

| #   | Call                                                                                                    | Shows                                     | Result                                                                                                                                |
| --- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `PATCH /api/activities/bulk-status` as `u-associate`: A → DONE (with note), B → DONE, `T-404` → BLOCKED | Sprint 1 per-item outcomes, Sprint 3 note | `200` · `updatedCount` 1, `failedCount` 2 · A success · B `FORBIDDEN` (status stays TODO) · T-404 `NOT_FOUND` "Task not found: T-404" |
| 2   | `GET /api/activities/{A}/audit` as `u-manager`                                                          | Sprint 3 audit entry                      | `200` · one entry: actor `u-associate`, TODO → DONE, note "Finished on shift"                                                         |
| 3   | `GET /api/activities/{A}/audit` as `u-associate`                                                        | Sprint 3 access rule (DEC-12)             | `403` `FORBIDDEN` "Only a store manager can read the audit trail"                                                                     |
| 4   | `PATCH /api/activities/bulk-status` with `{"updates":[]}`                                               | Sprint 2 whole-request validation         | `400` `VALIDATION_ERROR` "updates must not be empty"                                                                                  |

Request bodies were written to temp files and sent with `curl.exe --data @file` to avoid PowerShell quoting issues
(`curl` without `.exe` is PowerShell's `Invoke-WebRequest` alias).

## Notes and decisions

- **The image build is not a gate.** The Dockerfile packages with `-DskipTests` and skips Checkstyle, SpotBugs and
  JaCoCo, so `./mvnw verify` must run before the build (harness today; CI when added — CLAUDE.md "CI/CD", D19).
- **Local only, on purpose.** Authentication is a header stub: whoever sends `X-User-Id: u-manager` acts as the store
  manager. Windows Firewall access for Docker Desktop was **declined**, so the API is reachable from localhost only.
- **State is in memory.** Every container restart starts empty; the demo creates its own data.
- **Environment fix.** Docker Toolbox leftovers (`DOCKER_HOST=tcp://192.168.99.100:2376` and related variables,
  user scope) pointed the Docker CLI at a VM that no longer existed (journal 2026-10-18). Removed them; the CLI then
  reached Docker Desktop.
- **Teardown:** `docker compose down`.

## Cloud deployment — what it would require

Not done (time spent on the harness and documents instead). For AWS (ECS Fargate or Elastic Beanstalk, image from ECR):

- **Exposure:** the API has no real authentication, so a public endpoint lets anyone change data. Restrict the security
  group to known IPs, or run only for the demo and tear down.
- **Secrets:** the application has none (no database, no API keys). The only secrets are the deployer's AWS
  credentials — via an SSO/CLI profile, never in the repository or the image.
- **Configuration:** port 8080, a health-check path, JVM memory sized for a small task.
- **State and scaling:** in-memory storage → exactly one task; a second task would split the data, and every
  redeploy wipes it.
- **Pipeline:** build the image only after `./mvnw verify` passes in CI (D19); push to ECR; deploy.
- **Cost:** cents per hour for one small Fargate task (a load balancer adds a little); delete everything after use.
