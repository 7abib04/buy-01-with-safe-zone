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

## 3. Add the Git credential (one-time, do this yourself)

The pipeline clones `https://learn.reboot01.com/git/hmansoor/buy-01.git`, which
needs authentication. Jenkins will not have a working credential until you add
one — **do this directly in the Jenkins UI, not by handing the token to anyone
else**:

1. Jenkins → **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**
2. Kind: `Username with password`
3. Username: your reboot01 git username
4. Password: your reboot01 git access token
5. ID: `reboot-git-creds` (must match exactly — the Jenkinsfile and job definition reference this ID)

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

## 6. Stopping / resetting

```sh
docker compose down          # stop, keep Jenkins state (jobs, credentials)
docker compose down -v       # stop and wipe Jenkins state entirely
```
