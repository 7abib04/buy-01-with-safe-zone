#!/bin/sh
# Registers the buy01-cicd pipeline job with a running local Jenkins
# (jenkins/docker-compose.yml) via the REST API, using pipeline-job-config.xml.
# Safe to re-run: recreates the job if it already exists.
set -e

JENKINS_URL="${JENKINS_URL:-http://localhost:8090}"
JENKINS_USER="${JENKINS_ADMIN_ID:-admin}"
JENKINS_PASS="${JENKINS_ADMIN_PASSWORD:-admin123}"
JOB_NAME="buy01-cicd"

cd "$(dirname "$0")"

cookie_jar="$(mktemp)"
trap 'rm -f "$cookie_jar"' EXIT

crumb_json="$(curl -sf -c "$cookie_jar" -u "$JENKINS_USER:$JENKINS_PASS" "$JENKINS_URL/crumbIssuer/api/json")"
crumb="$(printf '%s' "$crumb_json" | grep -o '"crumb":"[^"]*"' | cut -d'"' -f4)"

if curl -sf -b "$cookie_jar" -u "$JENKINS_USER:$JENKINS_PASS" "$JENKINS_URL/job/$JOB_NAME/api/json" > /dev/null 2>&1; then
    echo "Job '$JOB_NAME' exists, updating its config..."
    curl -sf -b "$cookie_jar" -u "$JENKINS_USER:$JENKINS_PASS" -H "Jenkins-Crumb: $crumb" \
        -X POST "$JENKINS_URL/job/$JOB_NAME/config.xml" \
        -H "Content-Type: application/xml" \
        --data-binary @pipeline-job-config.xml
else
    echo "Creating job '$JOB_NAME'..."
    curl -sf -b "$cookie_jar" -u "$JENKINS_USER:$JENKINS_PASS" -H "Jenkins-Crumb: $crumb" \
        -X POST "$JENKINS_URL/createItem?name=$JOB_NAME" \
        -H "Content-Type: application/xml" \
        --data-binary @pipeline-job-config.xml
fi

echo "Done: $JENKINS_URL/job/$JOB_NAME/"
