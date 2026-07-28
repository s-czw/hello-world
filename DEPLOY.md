# Deploying Cairn

Self-hosted deployment runbook for the Cairn MVP: one `api` replica, one `web` replica,
PostgreSQL 16, Valkey 8, Traefik v3 for TLS, and a backup sidecar — all from
`docker-compose.prod.yml`.

> ## ⚠️ UNVERIFIED: the images in this stack have never been built
>
> The environment these files were authored in has a **policy-blocked container registry** —
> every `docker pull` returns `403 Forbidden`, so `docker build` and `docker compose up` could
> not be run even once. What *was* verified is listed in
> [What has and has not been verified](#what-has-and-has-not-been-verified) below.
>
> **Build this stack once on a Docker-capable host and complete a full first-run before you
> rely on it.** Treat the first `docker compose … up -d --build` as a task that may need
> fixing, not as a routine deploy. The most likely failure modes are base-image tags that have
> moved on and a `COPY` path that no longer matches the repo layout.

---

## 1. What the stack looks like

```
                internet
                   │  :80  → permanent redirect to :443
                   │  :443 → TLS (Let's Encrypt, HTTP-01) + security headers
             ┌─────▼──────┐
             │  traefik   │            network: cairn_edge
             └──┬──────┬──┘
   PathPrefix(/api)    │ everything else
             ┌──▼──┐ ┌─▼───┐
             │ api │ │ web │            api :8080, web :3000 (never published to the host)
             └──┬──┘ └─────┘
                │                       network: cairn_internal (internal: true — no egress)
        ┌───────┼─────────┐
    ┌───▼────┐ ┌▼──────┐ ┌▼───────┐
    │postgres│ │valkey │ │ backup │
    └────────┘ └───────┘ └────────┘
```

* **Only Traefik publishes ports** (80/443). Postgres and Valkey are on an `internal: true`
  network — unreachable from the host and from the internet, and with no outbound route.
* **`/api/*` is routed straight to the api container** (router priority 10) and everything else
  to Next.js (priority 1). The browser therefore talks to a single origin, which is what the
  `cairn_access` / `cairn_refresh` / `cairn_csrf` cookies assume.
* **Attachments** live on the `cairn_attachments` named volume, mounted in the api container at
  `CAIRN_STORAGE_DIR` (default `/data/attachments`) and read-only in the backup sidecar.
* **Background jobs run in-process** in the api container (`@Scheduled` notification purge) —
  there is deliberately no worker container, no PgBouncer and no object storage in the MVP.

### Files

| Path | Purpose |
| --- | --- |
| `docker-compose.yml` | **Dev only** — Postgres + Valkey for running the app on the host |
| `docker-compose.prod.yml` | The production/staging stack |
| `docker/Dockerfile.api` | Maven build → layered Spring Boot jar on `eclipse-temurin:21-jre` |
| `docker/Dockerfile.web` | pnpm build → Next.js standalone server on `node:22-alpine` |
| `docker/traefik/traefik.yml` | Traefik **static** config (entrypoints, ACME, providers) |
| `docker/traefik/dynamic/middlewares.yml` | Traefik **dynamic** config (security headers, TLS options) |
| `docker/backup/backup.sh` | Backup sidecar loop (nightly `pg_dump` + attachment tarball) |
| `.env.prod.example` | Template for `.env.prod` — every variable, documented |

---

## 2. Prerequisites

* A Linux host with **Docker Engine 24+** and the **Compose v2 plugin**
  (`docker compose version`). 2 vCPU / 4 GB RAM / 20 GB disk is comfortable for a 10–50 person
  team; the JVM sizes itself to 75 % of the container memory.
* **Ports 80 and 443 free and reachable from the internet.** The ACME HTTP-01 challenge needs
  port 80; do not put another reverse proxy in front without forwarding it.
* **A DNS A/AAAA record for `CAIRN_DOMAIN` already pointing at the host.** Let's Encrypt
  validates before the first certificate is issued — if DNS is not live, startup succeeds but
  HTTPS will not.
* The repository checked out on the host (the images are built from source; there is no
  published registry image).
* Outbound HTTPS from the host to Docker Hub, Maven Central and the npm registry — the image
  builds fetch dependencies.

---

## 3. First run

```bash
git clone <your-fork> cairn && cd cairn

# 1. Configuration
cp .env.prod.example .env.prod
chmod 600 .env.prod
$EDITOR .env.prod          # fill in CAIRN_DOMAIN, ACME_EMAIL, POSTGRES_PASSWORD, AUTH_JWT_SECRET

#    Generate the two secrets:
#      openssl rand -base64 24 | tr -d '=+/$'      # POSTGRES_PASSWORD
#      openssl rand -base64 48 | tr -d '=+/$'      # AUTH_JWT_SECRET  (>= 32 bytes)

# 2. Sanity-check the compose files resolve (catches typos and missing variables)
docker compose -f docker-compose.prod.yml --env-file .env.prod config -q

# 3. Mirror the ACME e-mail into Traefik's static config.
#    Traefik uses a single static-config source and its config FILE cannot read environment
#    variables, so this one value has to be written into the file:
. ./.env.prod && sed -i "s|changeme@example.com|${ACME_EMAIL}|" docker/traefik/traefik.yml
grep -n 'email:' docker/traefik/traefik.yml      # confirm it is yours, not the placeholder

# 4. Build and start
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 5. Watch it come up (postgres → valkey → api healthy → web)
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f api
```

Expected in the api log: Flyway applying `V1`…`V4` to an empty database, then
`Started CairnApiApplication`. Health:

```bash
docker exec cairn-api  curl -fsS http://127.0.0.1:8080/readyz   # {"status":"ok"} style 200
docker exec cairn-web  wget -qO- http://127.0.0.1:3000/icon.svg >/dev/null && echo web-ok
docker compose -f docker-compose.prod.yml --env-file .env.prod ps   # api/web/postgres/valkey healthy
```

Certificates: Traefik requests one on the first HTTPS request and stores it in the
`cairn_acme` volume. `docker compose … logs traefik | grep -i acme` shows the exchange. If it
fails, fix DNS/port 80 first — Let's Encrypt rate-limits failures.

> **Tip:** everything below assumes the same two flags. Export them once per shell:
> `alias dc='docker compose -f docker-compose.prod.yml --env-file .env.prod'`

### The bootstrap flow (creating the first account)

Cairn is **single-org** and ships with an empty database — there is no seeded admin and no
default password.

1. Open `https://<CAIRN_DOMAIN>/`. With no organization present the middleware redirects you to
   **`/setup`** (it asks the api `GET /api/v1/auth/bootstrap-status`).
2. Fill in the organization name, your name, e-mail and password. Submitting calls
   `POST /api/v1/auth/bootstrap`, which — in one transaction — creates the organization, your
   **admin** membership (argon2id password hash) and the default **General** team, then issues
   the session cookies and drops you on `/my-tasks`.
3. **Bootstrap is one-shot**: any later call returns `409 Conflict`. There is no way to re-run
   it without emptying the database, so do this yourself rather than leaving `/setup` open on a
   public host.
4. Invite the team: user menu → **Members** (admin only) → create an invite → copy the link.
   The invitee sets their own name and password at `/invite/accept?token=…`, becomes a
   **member**, and then logs in. Invites are single-use and expire after 14 days.
5. Promote a second admin straight away (Members → role → Admin). The last active admin cannot
   demote or deactivate themselves (`409`), so a single-admin org can lock you out of user
   management if that account is lost.

---

## 4. Day-2 operations

### Logs and status

```bash
dc ps                       # health of every service
dc logs -f api              # application log (JSON-file driver, 10 MB × 5 rotation)
dc logs traefik | grep -i acme
docker exec cairn-backup tail -n 50 /proc/1/fd/1 2>/dev/null || dc logs backup
```

### Updating to a new version

```bash
git pull
# Bump CAIRN_IMAGE_TAG in .env.prod (e.g. 2026-07-28) so the previous images stay identifiable.
dc build                    # build first; only restart once the build succeeded
BACKUP_ON_START=true dc up -d backup    # optional: take a fresh backup before switching
dc up -d
dc logs -f api              # Flyway applies any new migrations on boot
```

Migrations are **expand → migrate → contract**, always one version backward-compatible, so the
new api can start against the old schema and the old jar can run against the new schema. That
is what makes the rollback below safe.

### Rollback

```bash
# 1. Code/image rollback — the schema is backward-compatible by one version.
git checkout <previous-tag>
dc build && dc up -d

# 2. If that is not enough, restore the last backup (section below) and redeploy.
```

Rolling *back* a Flyway migration is not supported — restore from a dump instead. Never edit an
applied migration; add a new one.

### Backups

The `backup` sidecar runs every day at `BACKUP_HOUR_UTC:BACKUP_MINUTE_UTC` (default 03:15 UTC)
and writes into the `cairn_backups` volume:

```
/backups/db/cairn-<UTC timestamp>.dump                  pg_dump -Fc (custom, compressed)
/backups/attachments/attachments-<UTC timestamp>.tar.gz tar of CAIRN_STORAGE_DIR
```

Artefacts older than `BACKUP_RETENTION_DAYS` (default 14) are deleted after each successful
run. A run writes to a `.partial` file and renames on success, so a half-written dump is never
mistaken for a good one. **The database dump and the attachment snapshot from one run share a
timestamp — always restore the pair.**

```bash
# Run one now, without waiting for the schedule
docker exec cairn-backup sh -c 'BACKUP_ON_START=true /usr/local/bin/cairn-backup.sh' &
# …or simply:
dc restart backup            # with BACKUP_ON_START=true in .env.prod

# List what you have
docker run --rm -v cairn_backups:/b alpine ls -lh /b/db /b/attachments

# Copy a backup OFF the host — the volume lives on the same disk as the database.
docker run --rm -v cairn_backups:/b -v "$PWD":/out alpine \
  sh -c 'cp /b/db/cairn-20260728T031500Z.dump /b/attachments/attachments-20260728T031500Z.tar.gz /out/'
```

> **Off-host copies are your responsibility.** The sidecar only protects you from application
> and operator mistakes, not from losing the machine. Sync `cairn_backups` somewhere else
> (rsync/rclone/object storage) on your own schedule, and test a restore at least once.

### Restore

```bash
dc stop web api                       # no writers while restoring

# 1. Database — into a clean database, then let the api reconnect.
docker run --rm -i -v cairn_backups:/b --network cairn_internal \
  -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:16-alpine \
  pg_restore --clean --if-exists --no-owner -h postgres -U "$POSTGRES_USER" \
             -d "$POSTGRES_DB" /b/db/cairn-20260728T031500Z.dump

# 2. Attachments — replace the volume contents with the snapshot from the SAME timestamp.
docker run --rm -v cairn_attachments:/a -v cairn_backups:/b alpine \
  sh -c 'rm -rf /a/* && tar -xzf /b/attachments/attachments-20260728T031500Z.tar.gz -C /a'

dc start api web
dc logs -f api                        # Flyway should report the schema already at its version
```

If the dump is older than the deployed code, check out the matching tag and `dc build` before
starting the api, so Flyway migrates forward from the restored schema rather than finding a
newer one than it knows.

### Rotating secrets

* `AUTH_JWT_SECRET` — edit `.env.prod`, `dc up -d api`. Every issued access token becomes
  invalid; users are asked to log in again. Do this if the value ever leaks.
* `POSTGRES_PASSWORD` — change it **inside Postgres first**
  (`docker exec -it cairn-postgres psql -U cairn -c "ALTER USER cairn PASSWORD '…';"`), then in
  `.env.prod`, then `dc up -d api backup`. Changing only the env var locks the api out.

---

## 5. Configuration

`.env.prod.example` documents every variable. The four that must be set:

| Variable | Why |
| --- | --- |
| `CAIRN_DOMAIN` | Traefik routers + `APP_PUBLIC_URL` (invite links) |
| `ACME_EMAIL` | Let's Encrypt account — **also mirror into `docker/traefik/traefik.yml`** |
| `POSTGRES_PASSWORD` | Database credential (api + backup sidecar) |
| `AUTH_JWT_SECRET` | Session signing key, ≥ 32 bytes |

Two build-time gotchas worth knowing before you debug them the hard way:

* **`API_ORIGIN` is baked in at image build time.** Next.js inlines `process.env.API_ORIGIN`
  into the edge-middleware bundle and freezes the `/api/:path*` rewrite destination into the
  standalone server config. Setting it on a running container does nothing — it is a
  `--build-arg` (`http://api:8080` in `docker-compose.prod.yml`). This only affects the
  middleware's server-side `bootstrap-status` call, because Traefik routes `/api` itself.
* **Traefik's static config file cannot read environment variables**, and Traefik uses a single
  static-config source (file *or* CLI flags *or* env vars — not merged). Because
  `docker/traefik/traefik.yml` is mounted, it wins and any `command:` flags would be ignored.
  That is why the ACME e-mail is edited into the file (step 3) while the domain travels through
  Docker labels, which compose *does* interpolate.

---

## 6. Security notes

* TLS terminates at Traefik; HTTP is permanently redirected. HSTS (1 year, includeSubDomains),
  `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` and a restrictive
  `Permissions-Policy` are applied to every response by the `secure-headers@file` middleware.
* **The Traefik dashboard and API are disabled** (`api.dashboard: false`, `api.insecure: false`)
  and nothing is published on 8080. The `ping` entrypoint (8082) is container-local, for the
  HEALTHCHECK only.
* The Docker socket is mounted **read-only** into Traefik — it still grants effective root on
  the host, which is the standard trade-off for label-based routing. If that is unacceptable,
  replace the docker provider with a file provider pointing at static service URLs.
* Both application images run as a **non-root user** (uid 1001).
* `AUTH_COOKIE_SECURE=true` in production. Session cookies are httpOnly + SameSite=Lax; the
  `cairn_csrf` cookie is deliberately readable by same-origin JS (double-submit, arch §6.4).
* **Upgrade caveat:** a browser holding a session issued *before* the CSRF double-submit landed
  has no `cairn_csrf` cookie, so its first write returns `403`. It resolves itself on the next
  token refresh or re-login — worth a heads-up to the team on the upgrade that introduces it.
* Login is rate-limited to 5 attempts/minute per (IP, e-mail) with counters in Valkey.

---

## 7. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `required variable CAIRN_DOMAIN is missing a value` | `--env-file .env.prod` not passed, or the variable is absent |
| Browser shows a Traefik self-signed cert | ACME failed. Check DNS, port 80 reachability, and that the e-mail in `traefik.yml` is real. `dc logs traefik \| grep -i acme` |
| `api` stuck `starting` then `unhealthy` | `/readyz` pings the datasource — check `dc logs api` for Flyway/JDBC errors and that `POSTGRES_PASSWORD` matches what the volume was initialised with |
| `web` restarts / `depends_on` never satisfied | `web` waits for `api` to be **healthy**; fix the api first |
| 404s under `/api/...` | The api router lost to the web catch-all — check the `priority` labels resolved (`dc config \| grep priority`) |
| Attachments 404 after a restore | The tarball was extracted into the wrong volume, or `CAIRN_STORAGE_DIR` differs from the mount point in `docker-compose.prod.yml` |
| Backups not appearing | `dc logs backup` — the sidecar logs its next run time on start; check `PGPASSWORD` and that the schedule has actually elapsed |

---

## What has and has not been verified

**Verified in the authoring environment (commands actually run):**

* `docker compose -f docker-compose.yml config -q` → exit 0.
* `docker compose -f docker-compose.prod.yml --env-file .env.prod.example config -q` → exit 0,
  and the fully-resolved config was read back (interpolation, volumes, labels, `depends_on`
  conditions). Removing the env file makes it fail on the required-variable guards, so the
  check is not vacuous.
* Both Dockerfiles are parsed by BuildKit without syntax errors (the build then stops at the
  blocked registry when resolving the base images).
* The **Next.js standalone output** the web image ships was produced and run outside Docker:
  `apps/web/.next/standalone/apps/web/server.js` with `.next/static` copied alongside serves
  `/icon.svg` (the HEALTHCHECK target) 200, `/login` 200, `/my-tasks` → 307, and static chunks
  200 — the exact file layout the Dockerfile's two `COPY` lines create.
* The **layered Spring Boot jar** the api image ships was extracted with
  `java -Djarmode=tools -jar … extract --layers`, the four layer directories were overlaid the
  way the Dockerfile's four `COPY` lines do, and the resulting `app.jar` + `lib/` booted against
  a real PostgreSQL 16, applied all four Flyway migrations and answered `/readyz` 200.
* `docker/backup/backup.sh` passes `sh -n`, and its schedule arithmetic was executed for four
  target times (including the midnight wrap) and fired at the right minute in each case.

**NOT verified — the registry is policy-blocked, so no image was ever built or run:**

* That every base image tag resolves: `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`,
  `node:22-alpine`, `postgres:16-alpine`, `valkey/valkey:8-alpine`, `traefik:v3.3`.
* That `mvn package` and `pnpm build` succeed *inside* the builder stages (they succeed on the
  host).
* Any runtime behaviour of the composed stack: Traefik routing and ACME issuance, the two
  container HEALTHCHECKs, `depends_on: service_healthy` ordering, the volume mounts, and the
  backup sidecar's actual `pg_dump`/`tar` execution.

Run one full build + first-run on a Docker-capable host and fix what falls out before treating
this runbook as proven.
