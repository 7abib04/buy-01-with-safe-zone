import groovy.json.JsonOutput

def composeEnv() {
    return [
        "DEPLOY_ENV=${params.DEPLOY_ENV}",
        "COMPOSE_PROJECT_NAME=buy01-${params.DEPLOY_ENV}",
        'DOCKER_BUILDKIT=1',
        'COMPOSE_DOCKER_CLI_BUILD=1'
    ]
}

def healthCheckScript() {
    return '''
        set -e

        wait_for_service() {
            service="$1"
            attempts="${2:-30}"

            while [ "$attempts" -gt 0 ]; do
                container_id="$(docker compose ps -q "$service")"

                if [ -n "$container_id" ]; then
                    status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || echo unknown)"
                    echo "$service status: $status"

                    if [ "$status" = "healthy" ] || [ "$status" = "running" ]; then
                        return 0
                    fi
                fi

                attempts=$((attempts - 1))
                sleep 5
            done

            echo "$service did not become ready" >&2
            docker compose logs --tail=200 "$service" || true
            return 1
        }

        wait_for_service mongo 20
        wait_for_service minio 20
        wait_for_service discovery-service 30
        wait_for_service gateway-service 30
        wait_for_service user-service 30
        wait_for_service product-service 30
        wait_for_service media-service 30
        wait_for_service order-service 30
        wait_for_service frontend 20

        docker compose ps
        docker compose exec -T discovery-service curl -fsS http://localhost:8761/actuator/health
        docker compose exec -T gateway-service curl -fsS http://localhost:8080/actuator/health
        docker compose exec -T user-service curl -fsS http://localhost:8081/actuator/health
        docker compose exec -T product-service curl -fsS http://localhost:8082/actuator/health
        docker compose exec -T media-service curl -fsS http://localhost:8083/actuator/health
        docker compose exec -T order-service curl -fsS http://localhost:8084/actuator/health
        docker compose exec -T frontend curl -fsS http://localhost/healthz
    '''
}

def sendNotifications(String status, String details) {
    def message = """
        Status: ${status}
        Job: ${env.JOB_NAME} #${env.BUILD_NUMBER}
        Branch: ${env.GIT_BRANCH_NAME ?: params.GIT_BRANCH ?: env.BRANCH_NAME ?: 'unknown'}
        Environment: ${params.DEPLOY_ENV}
        Commit: ${env.GIT_COMMIT_SHORT ?: 'N/A'}
        URL: ${env.BUILD_URL}

        ${details}
    """.stripIndent().trim()

    echo message

    if (params.EMAIL_RECIPIENTS?.trim()) {
        try {
            mail(
                to: params.EMAIL_RECIPIENTS.trim(),
                subject: "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: message
            )
        } catch (err) {
            echo "Email notification failed: ${err.message}"
        }
    }

    if (params.SLACK_WEBHOOK_CREDENTIALS_ID?.trim()) {
        try {
            withCredentials([string(credentialsId: params.SLACK_WEBHOOK_CREDENTIALS_ID.trim(), variable: 'SLACK_WEBHOOK_URL')]) {
                writeFile(
                    file: 'slack-payload.json',
                    text: JsonOutput.toJson([text: message])
                )

                sh '''
                    set +x
                    curl -fsS -X POST \
                        -H "Content-Type: application/json" \
                        --data @slack-payload.json \
                        "$SLACK_WEBHOOK_URL"
                '''
            }
        } catch (err) {
            echo "Slack notification failed: ${err.message}"
        }
    }
}

def sonarQualityGateScript() {
    return '''
        set -e

        check_gate() {
            label="$1"
            report_task="$2"
            token="$3"

            if [ ! -f "$report_task" ]; then
                echo "$label: no $report_task found" >&2
                return 1
            fi

            ce_task_id="$(grep '^ceTaskId=' "$report_task" | cut -d= -f2-)"

            status="PENDING"
            for i in $(seq 1 60); do
                task_json="$(curl -s -u "$token": "$SONAR_HOST_URL/api/ce/task?id=$ce_task_id")"
                status="$(echo "$task_json" | jq -r '.task.status')"
                if [ "$status" = "SUCCESS" ] || [ "$status" = "FAILED" ] || [ "$status" = "CANCELED" ]; then
                    break
                fi
                sleep 5
            done

            if [ "$status" != "SUCCESS" ]; then
                echo "$label: background analysis task ended with status $status" >&2
                return 1
            fi

            analysis_id="$(echo "$task_json" | jq -r '.task.analysisId')"
            gate_json="$(curl -s -u "$token": "$SONAR_HOST_URL/api/qualitygates/project_status?analysisId=$analysis_id")"
            gate_status="$(echo "$gate_json" | jq -r '.projectStatus.status')"

            echo "$label: quality gate status = $gate_status"
            echo "$gate_json" | jq '.projectStatus.conditions'

            if [ "$gate_status" != "OK" ]; then
                echo "$label: quality gate failed" >&2
                return 1
            fi
        }

        failed=0
        check_gate "Backend (buy-01-backend)" "backend/target/sonar/report-task.txt" "$SONAR_BACKEND_TOKEN" || failed=1
        check_gate "Frontend (buy-01-frontend)" "frontend/.scannerwork/report-task.txt" "$SONAR_FRONTEND_TOKEN" || failed=1

        exit "$failed"
    '''
}

def collectComposeDiagnostics() {
    if (!fileExists("${env.APP_DIR}/docker-compose.yml")) {
        echo 'Compose diagnostics skipped because the application workspace is unavailable.'
        return
    }

    dir(env.APP_DIR) {
        withEnv(composeEnv()) {
            sh '''
                set +e
                docker compose ps || true
                docker compose logs --tail=200 || true
            '''
        }
    }
}

def rollbackDeployment() {
    if (!params.RUN_DEPLOY || !params.ROLLBACK_ON_FAILURE || env.DEPLOYMENT_STARTED != 'true') {
        env.ROLLBACK_RESULT = 'NOT_REQUIRED'
        echo 'Rollback skipped because deployment did not start or rollback is disabled.'
        return
    }

    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()) {
        env.ROLLBACK_RESULT = 'SKIPPED'
        echo 'Rollback skipped because Jenkins has no previous successful commit recorded yet.'
        return
    }

    if (!fileExists("${env.APP_DIR}/.git")) {
        env.ROLLBACK_RESULT = 'SKIPPED'
        echo 'Rollback skipped because the checked-out repository is unavailable.'
        return
    }

    env.ROLLBACK_RESULT = 'IN_PROGRESS'

    dir(env.APP_DIR) {
        withEnv(composeEnv()) {
            sh """
                set -e
                PREVIOUS_SUCCESSFUL_COMMIT="${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
                git fetch --all --tags --prune
                git checkout --detach "\$PREVIOUS_SUCCESSFUL_COMMIT"
                docker compose up -d --build --remove-orphans
            """

            sh healthCheckScript()
        }
    }

    env.ROLLBACK_RESULT = 'SUCCESS'
}

pipeline {
    agent any

    options {
        timestamps()
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        parallelsAlwaysFailFast()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20'))
    }

    parameters {
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Git branch to build and monitor.')
        choice(name: 'DEPLOY_ENV', choices: ['staging', 'production'], description: 'Deployment target/environment name.')
        booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Deploy after tests and image build succeed.')
        booleanParam(name: 'ROLLBACK_ON_FAILURE', defaultValue: true, description: 'Rollback to the last successful commit if deployment or health checks fail.')
        string(name: 'EMAIL_RECIPIENTS', defaultValue: '7abib2004@gmail.com', description: 'Comma-separated email recipients for build/deploy notifications.')
        string(name: 'SLACK_WEBHOOK_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins Secret Text credentials ID containing a Slack webhook URL.')
        string(name: 'SONAR_HOST_URL', defaultValue: 'http://sonarqube:9000', description: 'Persistent local SonarQube URL (see sonarqube/README.md). Reachable from this container via the shared buy01-sonarnet Docker network.')
        string(name: 'SONAR_BACKEND_TOKEN_CREDENTIALS_ID', defaultValue: 'sonarqube-backend-token', description: 'Jenkins Secret Text credentials ID containing the buy-01-backend SonarQube token. Leave blank to skip SonarQube analysis entirely.')
        string(name: 'SONAR_FRONTEND_TOKEN_CREDENTIALS_ID', defaultValue: 'sonarqube-frontend-token', description: 'Jenkins Secret Text credentials ID containing the buy-01-frontend SonarQube token. Leave blank to skip SonarQube analysis entirely.')
    }

    triggers {
        pollSCM('H/2 * * * *')
    }

    environment {
        APP_DIR = 'source'
        GIT_URL = 'https://github.com/7abib04/buy-02.git'
        GIT_CREDENTIALS_ID = '' // public repo, anonymous HTTPS clone — set a credential ID here if it's ever made private
        DEPLOYMENT_STARTED = 'false'
        ROLLBACK_RESULT = 'NOT_ATTEMPTED'
        GIT_COMMIT_SHORT = ''
        GIT_BRANCH_NAME = ''
        SONAR_HOST_URL = "${params.SONAR_HOST_URL}"
        // SONAR_ANALYSIS_DONE is deliberately NOT declared here: declarative
        // pipeline re-binds top-level `environment {}` values before every
        // stage, which would silently overwrite the env.SONAR_ANALYSIS_DONE
        // = 'true' set at runtime in the SonarQube Analysis stage right
        // before the next stage's `when` reads it. Leaving it undeclared
        // lets the runtime assignment stick; it defaults to null (falsy)
        // until that stage sets it.
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                dir(env.APP_DIR) {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${params.GIT_BRANCH}"]],
                        extensions: [
                            [$class: 'CleanBeforeCheckout'],
                            [$class: 'CloneOption', depth: 0, noTags: false, shallow: false]
                        ],
                        userRemoteConfigs: [[
                            credentialsId: env.GIT_CREDENTIALS_ID,
                            url: env.GIT_URL
                        ]]
                    ])

                    script {
                        env.GIT_BRANCH_NAME = params.GIT_BRANCH ?: sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                        env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.GIT_BRANCH_NAME}@${env.GIT_COMMIT_SHORT}"
                        currentBuild.description = "env=${params.DEPLOY_ENV}, deploy=${params.RUN_DEPLOY}"
                    }
                }
            }
        }

        stage('Notify Start') {
            steps {
                script {
                    sendNotifications('STARTED', 'Pipeline execution has started.')
                }
            }
        }

        stage('Verify Tools') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'node -v'
                sh 'npm -v'
                sh 'docker version'
                sh 'docker compose version'
                sh '''
                    CHROME_BIN_PATH="$(command -v chromium || command -v chromium-browser || command -v google-chrome || command -v google-chrome-stable || true)"
                    if [ -z "$CHROME_BIN_PATH" ]; then
                        echo "Chrome/Chromium binary not found" >&2
                        exit 1
                    fi
                    "$CHROME_BIN_PATH" --version
                '''
            }
        }

        stage('Backend Tests') {
            parallel {
                stage('User Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl user-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/user-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }

                stage('Product Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl product-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/product-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }

                stage('Order Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl order-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/order-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }

                stage('Media Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl media-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/media-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }

                stage('Gateway Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl gateway-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/gateway-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }

                stage('Discovery Service Tests') {
                    steps {
                        dir("${env.APP_DIR}/backend") {
                            sh 'mvn -B -ntp -pl discovery-service -am test'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${env.APP_DIR}/backend/discovery-service/target/surefire-reports/*.xml", allowEmptyResults: true
                        }
                    }
                }
            }
        }

        stage('Frontend Test') {
            steps {
                dir("${env.APP_DIR}/frontend") {
                    sh '''
                        set -e

                        CHROME_BIN_PATH="$(command -v chromium || command -v chromium-browser || command -v google-chrome || command -v google-chrome-stable || true)"
                        if [ -z "$CHROME_BIN_PATH" ]; then
                            echo "Chrome/Chromium binary not found" >&2
                            exit 1
                        fi

                        export CHROME_BIN="$CHROME_BIN_PATH"
                        npm ci
                        npm run test:ci
                    '''
                }
            }
            post {
                always {
                    junit testResults: "${env.APP_DIR}/frontend/test-results/junit.xml", allowEmptyResults: true
                    archiveArtifacts artifacts: "${env.APP_DIR}/frontend/coverage/**/*", allowEmptyArchive: true
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { return params.SONAR_BACKEND_TOKEN_CREDENTIALS_ID?.trim() && params.SONAR_FRONTEND_TOKEN_CREDENTIALS_ID?.trim() }
            }
            steps {
                script {
                    env.SONAR_ANALYSIS_DONE = 'false'
                    try {
                        withCredentials([
                            string(credentialsId: params.SONAR_BACKEND_TOKEN_CREDENTIALS_ID.trim(), variable: 'SONAR_BACKEND_TOKEN'),
                            string(credentialsId: params.SONAR_FRONTEND_TOKEN_CREDENTIALS_ID.trim(), variable: 'SONAR_FRONTEND_TOKEN')
                        ]) {
                            if (!env.SONAR_BACKEND_TOKEN?.trim() || !env.SONAR_FRONTEND_TOKEN?.trim()) {
                                echo 'SonarQube token credential(s) empty, skipping SonarQube analysis. See sonarqube/README.md to generate tokens, then export SONAR_TOKEN_BACKEND/SONAR_TOKEN_FRONTEND before `docker compose up` (jenkins/README.md).'
                                return
                            }

                            dir("${env.APP_DIR}/backend") {
                                sh 'mvn -B -ntp clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_BACKEND_TOKEN"'
                            }

                            dir("${env.APP_DIR}/frontend") {
                                sh 'sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_FRONTEND_TOKEN"'
                            }

                            env.SONAR_ANALYSIS_DONE = 'true'
                        }
                    } catch (err) {
                        echo "SonarQube analysis skipped: credential '${params.SONAR_BACKEND_TOKEN_CREDENTIALS_ID}' or '${params.SONAR_FRONTEND_TOKEN_CREDENTIALS_ID}' not usable (${err.message}). See sonarqube/README.md and jenkins/README.md."
                    }
                }
            }
        }

        stage('SonarQube Quality Gate') {
            when {
                expression { return env.SONAR_ANALYSIS_DONE == 'true' }
            }
            steps {
                dir(env.APP_DIR) {
                    withCredentials([
                        string(credentialsId: params.SONAR_BACKEND_TOKEN_CREDENTIALS_ID.trim(), variable: 'SONAR_BACKEND_TOKEN'),
                        string(credentialsId: params.SONAR_FRONTEND_TOKEN_CREDENTIALS_ID.trim(), variable: 'SONAR_FRONTEND_TOKEN')
                    ]) {
                        sh sonarQualityGateScript()
                    }
                }
            }
        }

        stage('Build Docker Images') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                dir(env.APP_DIR) {
                    withEnv(composeEnv()) {
                        sh '''
                            set -e
                            docker compose build --pull
                        '''
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                script {
                    env.DEPLOYMENT_STARTED = 'true'
                }
                dir(env.APP_DIR) {
                    withEnv(composeEnv()) {
                        sh '''
                            set -e
                            docker compose up -d --remove-orphans
                        '''
                    }
                }
            }
        }

        stage('Health Check') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                dir(env.APP_DIR) {
                    withEnv(composeEnv()) {
                        sh healthCheckScript()
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                def details = params.RUN_DEPLOY
                    ? 'Build, tests, deployment, and health checks all passed.'
                    : 'Build and tests passed. Deployment was skipped by parameter.'
                sendNotifications('SUCCESS', details)
            }
        }

        failure {
            script {
                collectComposeDiagnostics()

                try {
                    rollbackDeployment()
                } catch (rollbackError) {
                    env.ROLLBACK_RESULT = 'FAILED'
                    echo "Rollback failed: ${rollbackError.message}"
                }

                def details = """\
                    Pipeline failed.
                    Rollback result: ${env.ROLLBACK_RESULT}
                """.stripIndent().trim()

                sendNotifications('FAILURE', details)
            }
        }

        aborted {
            script {
                sendNotifications('ABORTED', 'Pipeline execution was aborted.')
            }
        }

        cleanup {
            // Declarative post-conditions always evaluate in a fixed order
            // (always, ..., aborted, failure, success, ..., cleanup) regardless
            // of their order in this file, so cleanWs() must live here rather
            // than in `always` — otherwise it wipes the workspace before the
            // `failure` block's rollback/diagnostics logic can read it.
            cleanWs()
        }
    }
}
