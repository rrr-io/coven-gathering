# The Coven Gathering — React + Spring Boot

A standalone version of The Coven Gathering website: **React + Vite + TypeScript**, **Java 21 + Spring Boot 3.5.16**, **PostgreSQL 16**, **Flyway** migrations, in-memory certificate recognition, and a KST scheduler. No dependency on ChatGPT, Cloudflare D1/R2, or the preview site.

The original layout and features are preserved: home page, four clans, selection of 249 ISO countries/territories, eight questions, tie-breakers, confirmation, participant dashboard, automatic certificate check-ins, leaderboards, regions, history, and admin configuration.

## Project structure

```text
frontend/                 React, Vite, TypeScript, CSS, Vitest tests
backend/                  Spring Boot, JDBC, REST, Flyway, integration tests
  src/main/java/org/coven/
    config/               Spring configuration and clock
    controller/           API endpoints and error handling
    security/             participant cookies, admin Basic auth, origin validation
    service/              quiz, registration, gathering engine, proofs, configuration
    repository/           parameterized SQL access
    scheduler/            daily reset and KST windows
  src/main/resources/
    config.json           initial configuration, editable by the admin
    countries.json        complete country dataset
    db/migration/         versioned database schema
  src/test/               API, database, cookie, security, and gathering rule tests
deploy/                   production Compose, Caddy, and backup script
.github/workflows/        tests and SSH deployment to Oracle
```

The deployment follows:

- Separate GitHub Actions jobs build the frontend and backend.
- The JAR is deployed to `/opt/coven-gathering/app.jar` on Oracle.
- React static files are deployed to `/srv/coven-gathering/`.
- Docker Compose runs the backend and PostgreSQL.
- The existing Caddy reverse proxy connects through the Docker `web` network.


## Run locally

Requirements: **Java 21**, **Maven 3.6.3+**, **Node 22.12+**, **Docker Compose v2**, and **Tesseract OCR with English language data**. On Ubuntu: `sudo apt-get install tesseract-ocr tesseract-ocr-eng`. On macOS: `brew install tesseract`.

From the project root:

```bash
cp .env.example .env
```

Edit `.env`: generate two different passwords with `openssl rand -hex 32`, one for the database and one for the admin. Do not commit this file.

Terminal 1:

```bash
set -a
. ./.env
set +a
docker compose up -d --wait
cd backend
mvn spring-boot:run
```

Terminal 2:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to Spring at `localhost:8080`. Local PostgreSQL uses port **5433**, leaving port 5432 available for your other project.

Admin: `http://localhost:5173/#admin`, username `admin`, and the password from your `.env`. The backend rejects admin passwords shorter than 16 characters; there is no default admin password.

## Deploy to Oracle — primary setup

### 1. Create a new repository

Upload **the contents of this directory** to the new repository. Keep `frontend`, `backend`, `deploy`, and `.github` at the repository root. The ZIP includes `.github`, which is required for deployment.

The workflow runs on pushes to `main`. Prepare the server and configure the following secrets before triggering it.

### 2. Prepare server directories and the network

Docker and the Caddy reverse proxy must already be installed. Your SSH deployment user must be able to run `docker` without interactive sudo.

```bash
sudo mkdir -p /opt/coven-gathering /srv/coven-gathering
sudo chown "$USER:$USER" /opt/coven-gathering /srv/coven-gathering
docker network inspect web >/dev/null 2>&1 || docker network create web
```

If deployment uses a different user from the one running these commands, assign the directories to that user instead. Do not change permissions on the existing application directories.

### 3. DNS and Caddy

Choose a new subdomain, such as `coven.rrriooo.com`, and point its DNS record to your Oracle VM's IP address.

Add the block from `deploy/Caddyfile.example` to your **existing Caddyfile**, replacing `coven.example.com` with your chosen domain. Do not replace the entire Caddyfile.

Caddy must:

- Be connected to the Docker `web` network.
- Have access to the frontend files. If it already mounts `/srv:/srv:ro`, no additional volume is needed. Otherwise, add `/srv/coven-gathering:/srv/coven-gathering:ro` to the Caddy container configuration.
- Forward `/api/*` to `coven-backend:8080` **without stripping `/api`**.

Validate and reload Caddy using the container name and configuration path for your installation. For example, if the container is named `caddy`:

```bash
docker exec caddy caddy validate --config /etc/caddy/Caddyfile
docker exec caddy caddy reload --config /etc/caddy/Caddyfile
```

Use the HTTP/HTTPS ports already served by Caddy. Do not expose PostgreSQL or port 8080 publicly; the production Compose file does not publish either port.

### 4. GitHub secrets and variables

In the new repository's **Settings → Secrets and variables → Actions**:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `SSH_HOST` | Oracle VM IP address or hostname |
| Secret | `SSH_USER` | Deployment user |
| Secret | `SSH_KEY` | Deployment SSH private key |
| Secret | `DB_PASSWORD` | New password, preferably generated with `openssl rand -hex 32` |
| Secret | `ADMIN_PASSWORD` | Separate admin password, at least 16 characters |
| Variable | `APP_ORIGINS` | `https://coven.rrriooo.com` — no trailing slash |
| Variable | `FRONTEND_HOST` | `oracle`, or leave empty |

You can reuse the host, user, and SSH key from your existing deployment, but use new passwords for this application. The workflow automatically populates `known_hosts` with `ssh-keyscan` during deployment, trusting the host key returned by the server. No `SSH_KNOWN_HOSTS` secret is required.

Passwords supplied through the pipeline may contain letters, digits, and `._~+/=-`. Hexadecimal passwords match this format and avoid interpolation issues in `.env` files.

### 5. Push to main

```bash
git init
git add .
git commit -m "Initial Coven Gathering React and Spring Boot app"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
git push -u origin main
```

The workflow runs tests, builds `frontend/dist` and `backend/target/app.jar`, copies them to the server, and waits for PostgreSQL and the backend to become healthy. It then updates the frontend. The previous JAR is retained as `app.jar.previous`; participants and archived statistics are preserved. The upgrade removes legacy stored proof images after the new backend is healthy.

Check the server:

```bash
cd /opt/coven-gathering
docker compose ps
docker compose logs --tail=100 backend
```

In the browser, `https://YOUR-DOMAIN/api/health` should return `{"status":"UP"}`. The admin page is `https://YOUR-DOMAIN/#admin`.

### Manual deployment without GitHub Actions

After configuring `.env`, the directories, and Caddy:

```bash
(cd frontend && npm ci && npm test && npm run build)
(cd backend && mvn verify)
scp backend/target/app.jar deploy/docker-compose.yml deploy/Dockerfile YOUR-USER@YOUR-SERVER:/opt/coven-gathering/
rsync -az --delete frontend/dist/ YOUR-USER@YOUR-SERVER:/srv/coven-gathering/
```

Create `/opt/coven-gathering/.env` on the server with `DB_PASSWORD`, `ADMIN_PASSWORD`, `ADMIN_USER=admin`, and `APP_ORIGINS=https://YOUR-DOMAIN`. Restrict its permissions with `chmod 600 .env`, then run:

```bash
cd /opt/coven-gathering
docker compose up -d --build --wait --wait-timeout 180
```

## Alternative: Vercel frontend, Oracle backend

This setup keeps **Spring Boot, PostgreSQL, the scheduler, and check-in counters on Oracle** and deploys only React to Vercel. It does not require Vercel to run a Java JAR or store uploads in a function's temporary filesystem.

1. Prepare the Oracle backend as described above.
2. Use `deploy/Caddyfile.api.example` for a dedicated HTTPS subdomain, such as `api-coven.rrriooo.com`.
3. In `frontend/vercel.json`, replace **`https://api-coven.example.com`** with the actual API origin, keeping `/api/:path*`.
4. Import the repository into Vercel and set **Root Directory: `frontend`**, framework **Vite**, Node **22.x**, build command `npm run build`, and output directory `dist`.
5. Set `FRONTEND_HOST=vercel` in GitHub. The pipeline continues deploying the backend to Oracle and skips copying React files to `/srv`.
6. Set `APP_ORIGINS` to the exact Vercel frontend origin. If you use both a custom domain and `project-name.vercel.app`, separate them with a comma, without spaces or trailing slashes.
7. Redeploy the backend to apply `APP_ORIGINS`.

The browser always calls `/api` on the frontend domain. Rewrites forward those requests to Oracle, keeping cookies and browser requests on a single origin. Open CORS rules or tokens in localStorage are not required. Vercel Preview domains are not automatically authorized; add their exact origins to test them.

Once deployed, verify registration, cookies, admin sign-in, and uploads through the rewrite as well. The Vercel configuration is included, but it has not been deployed or tested in your account.

## Identity and privacy

- Public visitors see aggregate statistics only.
- Participants receive a random `HttpOnly`, `SameSite=Lax` cookie, marked `Secure` in production and valid for one year. Only its hash is stored in the database.
- Admin authentication uses HTTP Basic over HTTPS. Credentials stay in page memory and are lost on reload. Every operation is authorized server-side.
- Certificate uploads accept PNG/JPG up to 5 MB and 16 megapixels. Multipart uploads stay in memory; Tesseract receives bytes on stdin. The image, OCR text, readable certificate number and URL are not stored or logged. There is no image download endpoint.
- Minimal check-in receipts retain participant/window associations, timestamps and a SHA-256 certificate-number fingerprint to enforce one check-in per window and prevent certificate reuse. Aggregate counters stay on the server. The UI and statistics API expose certificate participation percentages and membership shares, including history and coordination, without participant or proof counts.

This version **does not introduce ENGENE accounts**. The cookie identifies a browser, not a unique person: another browser or clearing cookies creates a new identity, and registration cannot be recovered. This supports a participation flow without sign-in; the clan remains locked for that identity. Data from the ChatGPT preview site is not imported automatically.

## Gathering integrity

- Selecting a country does not create a participant.
- Completing the quiz creates only a private quiz result.
- Only `ACCEPT MY CLAN` creates an active participant. Repeated confirmation is idempotent.
- The server calculates the clan. Tie-breakers only select among clans that are actually tied.
- Denominators use registrations timestamped **before the window starts**. Participants who join during a window become eligible for the next one.
- A SQL constraint prevents two proofs from the same participant within one window.
- A normalized certificate-number fingerprint detects reuse even if the file is recompressed. A database lock and unique constraint protect concurrent submissions.
- Recognized certificates immediately receive the internal `VERIFIED` status, displayed as **ACCEPTED**. No administrator approval is needed. Failed scans and duplicates do not consume the slot, so participants can retry.
- `@Scheduled`, using `Asia/Seoul`, closes the day at midnight. A tick every 15 seconds and reconciliation on each request catch up after backend downtime.
- Historical snapshots preserve final results and denominators. Legacy pending submissions are rejected by the upgrade, never retroactively accepted.
- A PostgreSQL transaction lock serializes gathering changes, including simultaneous requests.

The scheduler treats midnight as the logical boundary. If the JVM or server is offline, execution cannot occur at that exact instant; after restart, results are reconstructed using the original timestamps.

## Admin configuration

The `/#admin` page includes coordination, and these configuration sections: General, Clans, Quiz, Quiz questions, Quiz scoring, Schedules, Countries, Voting regions, MAMA guide, Website content, Banners, and Admin settings.

The editor uses **JSON**, validated by the backend. Initial configuration comes from `backend/src/main/resources/config.json` until a configuration is saved in the database. Editing the file after the first admin save does not overwrite the database configuration.

Window changes take effect on the **next KST day**. The IDs of the four clans and four windows remain stable. Country region/timezone changes apply to **new registrations**, without changing existing participants. MAMA information starts empty and is displayed only with source links; no official rules have been invented.

Example override in `Countries`:

```json
{
  "PH": { "region": "SEA", "timezone": "Asia/Manila" }
}
```

`Admin settings` contains operational text, not usernames or passwords. Manage credentials through environment variables.

## Tests

```bash
(cd frontend && npm ci && npm test && npm run build)
(cd backend && mvn verify)
```

Frontend: seven tests covering sorting, tie-breakers, KST boundaries, percentages, and request handling. Backend: Spring/MockMvc tests using the Flyway schema and H2 in PostgreSQL mode, plus pure rule tests. The API test covers the six original acceptance scenarios, repeated confirmation, closed windows, late registration, duplicate proofs, removed image access, automatic certificate acceptance, certificate OCR, admin authorization, rejection of untrusted origins, configuration changes, and immutable history.

Local tests do not use production databases. The GitHub Actions backend jobs are also configured to run against a PostgreSQL 16 service. Deployment to your Oracle server and browser-based visual end-to-end testing have not been performed.

## Persistence and backups

Volume: `coven-gathering_coven-db-data`. New certificates have no persistent file volume. Keep the Compose project name `coven-gathering`. `docker compose down` preserves volumes; **do not use `down -v` on data you need to keep**.

`deploy/backup.sh` briefly stops the backend, saves a PostgreSQL dump, then restarts the backend. Run it on the server with a private backup directory:

```bash
bash /path/to/backup.sh /path/to/coven-backups
```

Applied Flyway migrations are immutable. For future changes, add `V2__...sql`, `V3__...sql`, and so on. Rolling back the JAR alone does not undo database migrations.

## Sources and dependencies

- Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Vercel external rewrites: https://vercel.com/docs/routing/rewrites
- Caddy reverse proxy: https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- Countries: https://github.com/mledoze/countries (ODbL 1.0); derived geographic data in `countries.json`.
- Timezones: IANA via pytz; offsets are calculated dynamically, including daylight saving changes.
- Fonts: Cormorant Garamond and Manrope via Google Fonts, with local fallbacks.

This project is fan-made and is not affiliated with or endorsed by MAMA, CJ ENM, HYBE, ENHYPEN, or Twilight. Rankings represent gathering participation, not official voting results.


Tesseract CLI reference: https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html
