# SonarQube for buy-01

A persistent, local SonarQube (Community Edition) instance for this repo: browsable
dashboard, historical trend data, SonarLint IDE binding, and the token source for the
[Jenkins pipeline](../jenkins/README.md)'s SonarQube stage.

This is **separate** from the SonarQube instance used in
[`.github/workflows/sonarqube.yml`](../.github/workflows/sonarqube.yml): GitHub Actions
spins up its own throwaway SonarQube container inside every CI run (so pushes/PRs are
gated with zero setup, from any machine), while this one is the long-lived server you
browse day to day.

Two SonarQube projects are used for this monorepo (multi-service, one Maven reactor +
one Angular app):

| Project key       | Source                                             |
|--------------------|-----------------------------------------------------|
| `buy-01-backend`  | `backend/` — Maven reactor (5 Spring Boot services) |
| `buy-01-frontend` | `frontend/` — Angular app                           |

## 1. Start it

```sh
cd sonarqube
docker compose up -d
```

First boot takes a minute or two (Elasticsearch + schema migration). Then open
http://localhost:9000.

## 2. First login

- Login: `admin` / `admin`
- You'll be forced to set a new password immediately — do this before anything else.
  Local single-user dev instance or not, don't leave the default password in place.

## 3. Create the two projects and generate tokens

**UI**: **Projects → Create Project → Manually**, project key/display name from the
table above, then **Locally** analysis method to get to the token screen (you don't
need to run the scanner from that screen — the CLI commands below or the Jenkins
pipeline will do the actual analysis).

**Or via API** (same result, scriptable — replace `<ADMIN_TOKEN>` with a token you
generate for your own admin user under **My Account → Security**):

```sh
curl -u <ADMIN_TOKEN>: -X POST http://localhost:9000/api/projects/create \
  -d "project=buy-01-backend&name=Buy-01 Backend"

curl -u <ADMIN_TOKEN>: -X POST http://localhost:9000/api/projects/create \
  -d "project=buy-01-frontend&name=Buy-01 Frontend"

# Analysis token for the backend project (repeat with a different "name" for frontend)
curl -u <ADMIN_TOKEN>: -X POST http://localhost:9000/api/user_tokens/generate \
  -d "name=buy-01-backend-analysis"
```

Save the generated token — SonarQube only shows it once. You'll need it for:

- Running a manual scan from your machine (below)
- The Jenkins pipeline (`SONAR_TOKEN` env var, see [`jenkins/README.md`](../jenkins/README.md))

## 4. Run a manual scan (optional — CI does this automatically)

Backend (from `backend/`, needs a local Maven + JDK 17):

```sh
mvn -B clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<BACKEND_TOKEN>
```

Frontend (from `frontend/`, needs a local Node 20 and the
[sonar-scanner CLI](https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner/)
on your `PATH`):

```sh
npm ci
npm run test:ci
sonar-scanner -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<FRONTEND_TOKEN>
```

`frontend/sonar-project.properties` and the `sonar.projectKey`/`jacoco` settings in
`backend/pom.xml` already carry the rest of the configuration (source paths, coverage
report paths, exclusions) — see those files for details.

## 5. Quality gate

Both projects use the built-in **"Sonar way"** quality gate (no changes needed): fails
on new-code coverage < 80%, new-code duplication > 3%, or any new Blocker/Critical
issue, security hotspot left un-reviewed, or a reliability/security/maintainability
rating worse than A on new code. Customize under **Quality Gates** in the UI if the
project's needs differ — both the GitHub Actions workflow and the Jenkins stage read
whatever gate is currently assigned to the project, so a UI change takes effect on the
next CI run automatically.

## 6. Permissions / access control

Out of the box, `admin` is the only user with rights, which is correct for a
single-developer local instance. If you add collaborators:

- **Administration → Security → Users** — create one account per person, no shared
  logins.
- **Administration → Security → Global Permissions** (or per-project **Project
  Settings → Permissions**) — grant `Browse`/`See Source Code` broadly if useful, but
  keep `Administer` and `Administer Quality Gates/Profiles` limited to whoever owns the
  Sonar setup. `Execute Analysis` only needs to be granted to the CI token's user, not
  every human account.
- Rotate/revoke tokens under **My Account → Security** if a token ever leaks (e.g. it
  ends up in a shell history file).

## 7. Notifications (bonus)

SonarQube has built-in analysis notifications per user (failed quality gate, new
issues assigned, etc.) under **Administration → Configuration → General → Email** for
SMTP setup, then each user opts in under **My Account → Notifications**. For local dev
you can point it at the same MailHog catcher the Jenkins stack already runs
(`jenkins/docker-compose.yml`, SMTP on `localhost:1025`, inbox UI at
http://localhost:8025) — no real mail credentials required. For real delivery, use your
provider's SMTP host/port/credentials the same way you would with any other tool (never
paste that password to an AI assistant — type it directly into the SonarQube UI field).

Slack notifications for CI results are handled outside SonarQube itself, in the CI
layer — see the `Notify Slack on quality gate failure` step in
[`.github/workflows/sonarqube.yml`](../.github/workflows/sonarqube.yml) (needs a repo
secret named `SLACK_WEBHOOK_URL`) and the existing `sendNotifications()` Slack path in
the [`Jenkinsfile`](../Jenkinsfile).

## 8. SonarLint IDE integration (bonus)

Install the SonarLint extension ([VS Code](https://marketplace.visualstudio.com/items?itemName=SonarSource.sonarlint-vscode),
[IntelliJ/JetBrains](https://plugins.jetbrains.com/plugin/7973-sonarlint)), then:

1. SonarLint panel → **Connected Mode** → **Add connection** → SonarQube Server →
   `http://localhost:9000` → paste a personal access token (generate one under
   **My Account → Security**, separate from the CI analysis tokens above).
2. Bind the `backend` folder (or each service, if your IDE binds per-module) to the
   `buy-01-backend` project, and the `frontend` folder to `buy-01-frontend`.

Once connected, SonarLint flags issues inline as you type using the same rules and
quality profile as the server, instead of only finding out at CI time.

## 9. Review process

See the top-level [`.github/pull_request_template.md`](../.github/pull_request_template.md)
and the "Review & approval process" section of the root [`README.md`](../README.md) for
how this ties into pull requests.

## 10. Stopping / resetting

```sh
docker compose down          # stop, keep all SonarQube data (projects, history, users)
docker compose down -v       # stop and wipe it entirely (start over from step 2)
```
