# Jenkins CI/CD for buy-01

This directory stands up a self-contained Jenkins controller for running the
[`Jenkinsfile`](../Jenkinsfile) at the repo root. It bundles everything the
pipeline needs: Docker CLI (for `docker compose build/up`), Maven + JDK 17
(backend tests), Node 20 (frontend build), and headless Chromium (Karma/Jasmine
tests). It also runs a local [MailHog](https://github.com/mailhog/MailHog)
SMTP catcher so build notification emails can be viewed in a browser without
needing real mail credentials.

## 1. Start Jenkins

```sh
cd jenkins
docker compose up -d --build
```

First boot takes a minute or two while Jenkins Configuration-as-Code (JCasC)
provisions the plugins, a local admin account, and mail settings.

- Jenkins UI: http://localhost:8090 (mapped away from 8080 so the pipeline's
  own `docker compose up` can bind the app's gateway-service to host port 8080
  without colliding with Jenkins itself — both run as siblings on your host
  Docker daemon since this setup uses Docker-outside-of-Docker)
- Login: `admin` / `admin123` (set via `JENKINS_ADMIN_ID` / `JENKINS_ADMIN_PASSWORD`
  in [`docker-compose.yml`](docker-compose.yml) — change these before using this
  anywhere other than your own machine)
- MailHog inbox (build notification emails): http://localhost:8025

## 2. Create the pipeline job

The `buy01-cicd` job isn't provisioned by JCasC (Job DSL's `git {}` closure
didn't resolve cleanly against this plugin set and caused a boot crash-loop),
so it's created via a small script that POSTs
[`pipeline-job-config.xml`](pipeline-job-config.xml) to the Jenkins REST API
instead. Re-run it any time to reset the job to this repo's definition:

```sh
sh create-job.sh
```

## 3. Git access

The pipeline clones `https://github.com/7abib04/buy-01-with-safe-zone.git`
anonymously over HTTPS — it's a public repo, so no credential is required and
there's nothing to set up here. If the repo is ever made private, add a
`Username with password` credential (username + a GitHub personal access
token as the password — enter it directly in the Jenkins UI, don't hand it to
anyone else) and reference its ID from `GIT_CREDENTIALS_ID` in the
[`Jenkinsfile`](../Jenkinsfile) and `<credentialsId>` in
[`pipeline-job-config.xml`](pipeline-job-config.xml).

## 4. Run a build

Jenkins → job **buy01-cicd** → **Build with Parameters**. Defaults:

| Parameter | Default | Notes |
|---|---|---|
| `GIT_BRANCH` | `main` | branch to build |
| `DEPLOY_ENV` | `staging` | passed through as `COMPOSE_PROJECT_NAME` suffix |
| `RUN_DEPLOY` | `true` | build images + `docker compose up` + health check |
| `ROLLBACK_ON_FAILURE` | `true` | on failure, redeploy the last successful commit |
| `EMAIL_RECIPIENTS` | `7abib2004@gmail.com` | who gets notified |
| `SLACK_WEBHOOK_CREDENTIALS_ID` | *(empty)* | optional, see below |

The pipeline: checkout → notify start → verify tools → parallel backend
JUnit tests (one stage per Maven module) → frontend Jasmine/Karma tests →
build Docker images → `docker compose up` deploy → health checks against every
service's `/actuator/health` (and the frontend's `/healthz`) → notify
success/failure. Any stage failing stops the pipeline; a deploy/health-check
failure triggers rollback to the last successful commit when
`ROLLBACK_ON_FAILURE` is enabled.

Because this Jenkins container has `/var/run/docker.sock` bind-mounted from
the host, `docker compose up` inside the pipeline starts containers as
siblings on your host Docker, the same way as running it from a terminal.

## 5. Notifications

- **Email** ships two ways to receive it:
  - Out of the box against [MailHog](https://github.com/mailhog/MailHog) (a
    local SMTP catcher, no real credentials needed) — every build sends a
    start/success/failure email to `EMAIL_RECIPIENTS`, viewable instantly at
    http://localhost:8025.
  - For real delivery, configure real SMTP yourself in Jenkins UI →
    **Manage Jenkins → System → E-mail Notification** (host, port, a
    username, and a password/app-password you enter directly in that field —
    never share it with anyone else, including an AI assistant). This
    intentionally isn't managed by [`casc/jenkins.yaml`](casc/jenkins.yaml)
    so it survives restarts undisturbed; see the comment there. It's only
    lost if you wipe the `jenkins_home` volume (`docker compose down -v`).
- **Slack** is implemented in the Jenkinsfile (`sendNotifications`) but needs
  a webhook to actually post anywhere. To enable it: create an [Incoming
  Webhook](https://api.slack.com/messaging/webhooks) in your Slack workspace,
  add it in Jenkins UI as a **Secret text** credential, then pass that
  credential's ID as the `SLACK_WEBHOOK_CREDENTIALS_ID` build parameter.

## 6. SonarQube quality gate

The pipeline has a `SonarQube Analysis` + `SonarQube Quality Gate` stage that scans
both `buy-01-backend` and `buy-01-frontend` against the persistent local SonarQube
instance in [`../sonarqube`](../sonarqube/README.md) and **fails the build** if either
project's quality gate doesn't pass. It's opt-in via a credential so the pipeline still
runs out of the box before you've set SonarQube up:

1. Start the SonarQube stack first so its Docker network exists before Jenkins joins
   it: `cd sonarqube && docker compose up -d` (see that folder's README for first
   login and creating the two projects).
2. Generate one analysis token per project in the SonarQube UI (**My Account →
   Security**, or during each project's setup flow) — a token generated that way is
   often scoped to just that one project, so keep the backend and frontend tokens
   separate rather than trying to reuse one.
3. Export both before starting Jenkins so JCasC picks them up as the
   `sonarqube-backend-token` / `sonarqube-frontend-token` credentials (see
   [`casc/jenkins.yaml`](casc/jenkins.yaml)):

   ```powershell
   $env:SONAR_TOKEN_BACKEND = "<paste the backend project token>"
   $env:SONAR_TOKEN_FRONTEND = "<paste the frontend project token>"
   cd jenkins
   docker compose up -d --build
   ```

   ```sh
   export SONAR_TOKEN_BACKEND="<paste the backend project token>"
   export SONAR_TOKEN_FRONTEND="<paste the frontend project token>"
   cd jenkins
   docker compose up -d --build
   ```

4. Build with parameters as usual. `SONAR_HOST_URL` already defaults to
   `http://sonarqube:9000` (the two stacks share the `buy01-sonarnet` Docker network),
   and `SONAR_BACKEND_TOKEN_CREDENTIALS_ID` / `SONAR_FRONTEND_TOKEN_CREDENTIALS_ID`
   already default to `sonarqube-backend-token` / `sonarqube-frontend-token` — leave
   all of these as-is unless you renamed something.

If either token was never set (or the credential lookup fails), the analysis stage
logs a warning and skips instead of failing the whole pipeline — useful before you've
done the one-time setup above, but it means the quality gate isn't actually enforced
until you do it.

## 7. Stopping / resetting

```sh
docker compose down          # stop, keep Jenkins state (jobs, credentials)
docker compose down -v       # stop and wipe Jenkins state entirely
```
