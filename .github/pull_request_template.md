## What changed and why

<!-- Short description of the change and the motivation behind it. -->

## Checklist

- [ ] Backend tests pass locally (`cd backend && mvn -q clean test`) or in CI
- [ ] Frontend tests pass locally (`cd frontend && npm run test:ci`) or in CI
- [ ] [SonarQube Quality Gate](../actions/workflows/sonarqube.yml) is green for both
      `buy-01-backend` and `buy-01-frontend`
- [ ] No new Blocker/Critical issues or un-reviewed security hotspots were introduced
      — or, if there are any, they're justified below with a reason they're
      acceptable (false positive, accepted risk, tracked follow-up, etc.)
- [ ] At least one reviewer has approved

## SonarQube findings needing justification (if any)

<!-- List any quality-gate-adjacent issues that are being knowingly left as-is, and why. -->

## How to verify

<!-- Steps a reviewer can follow to see this working, e.g. docker compose up --build and hit an endpoint. -->
