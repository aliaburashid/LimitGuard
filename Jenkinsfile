// LimitGuard CI/CD (Jenkins on a Mac): test -> build 6 images -> push to Amazon ECR -> deploy to EC2.
//
// Files used from the repository:
//   Jenkinsfile, deploy/aws/compose.prod.yaml (the production stack), database/schema.sql (tables + data)
//
// Jenkins configuration (Manage Jenkins):
//   Plugins:      suggested plugins + "SSH Agent"
//   Credentials:  limitguard-aws      Username with password  (IAM access key ID / secret access key)
//                 limitguard-ec2-ssh  SSH Username with private key (user ec2-user, the .pem key)
//                 limitguard-env      Secret file  (the production .env: RDS, JWT, Gmail, ...)
//   Environment:  LIMITGUARD_APP_HOST    EC2 Elastic IP
//                 LIMITGUARD_AWS_REGION  e.g. me-south-1
//                 JAVA_HOME              e.g. /opt/homebrew/opt/openjdk@21
//                 LIMITGUARD_BRANDFETCH_CLIENT_ID  (optional, company logos)

def SERVICES = [
    // ECR repository : build folder
    [name: 'auth',     dir: 'auth-service'],
    [name: 'credit',   dir: 'credit-service'],
    [name: 'activity', dir: 'activity-service'],
    [name: 'gateway',  dir: 'api-gateway'],
    [name: 'ml',       dir: 'ml-service'],
    [name: 'frontend', dir: 'frontend'],
]

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 120, unit: 'MINUTES')
    }

    // Check GitHub every 5 minutes for new commits.
    triggers {
        pollSCM('H/5 * * * *')
    }

    parameters {
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run backend, frontend and ML tests')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy to EC2 after pushing the images')
    }

    environment {
        AWS_REGION     = "${env.LIMITGUARD_AWS_REGION ?: 'me-south-1'}"
        APP_HOST       = "${env.LIMITGUARD_APP_HOST ?: ''}"
        PROJECT        = 'limitguard'
        COMPOSE_FILE_PATH = 'compose.prod.yaml'
        // EC2 (t3) is x86; an Apple Silicon Mac must build x86 images explicitly.
        IMAGE_PLATFORM = 'linux/amd64'
        // Jenkins started by Homebrew does not see Homebrew or Docker Desktop commands by default.
        PATH = "/opt/homebrew/bin:/usr/local/bin:/Applications/Docker.app/Contents/Resources/bin:${env.PATH}"
    }

    stages {

        stage('Prepare') {
            steps {
                script {
                    if (!env.APP_HOST?.trim()) {
                        error('Add LIMITGUARD_APP_HOST (the EC2 Elastic IP) in Manage Jenkins > System > Environment variables.')
                    }
                    withCredentials([usernamePassword(credentialsId: 'limitguard-aws',
                            usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                        env.AWS_ACCOUNT_ID = sh(script: 'aws sts get-caller-identity --query Account --output text', returnStdout: true).trim()
                    }
                    env.REGISTRY  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    currentBuild.description = "tag ${env.IMAGE_TAG}"
                }
                sh '''
                    echo "Registry: $REGISTRY  tag: $IMAGE_TAG  server: $APP_HOST"
                    echo "JAVA_HOME: ${JAVA_HOME:-not set}"
                    docker version --format 'Docker {{.Server.Version}}'
                '''
            }
        }

        stage('Test') {
            when { expression { params.RUN_TESTS } }
            stages {
                stage('Auth Service') {
                    steps { dir('auth-service') { sh 'bash ./mvnw -B -ntp test' } }
                    post { always { junit allowEmptyResults: true, testResults: 'auth-service/target/surefire-reports/*.xml' } }
                }
                stage('Credit Service') {
                    steps { dir('credit-service') { sh 'bash ./mvnw -B -ntp test' } }
                    post { always { junit allowEmptyResults: true, testResults: 'credit-service/target/surefire-reports/*.xml' } }
                }
                stage('Activity Service') {
                    steps { dir('activity-service') { sh 'bash ./mvnw -B -ntp test' } }
                    post { always { junit allowEmptyResults: true, testResults: 'activity-service/target/surefire-reports/*.xml' } }
                }
                stage('API Gateway') {
                    steps { dir('api-gateway') { sh 'bash ./mvnw -B -ntp -DskipTests package' } }
                }
                stage('Frontend') {
                    steps {
                        sh 'docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$WORKSPACE/frontend:/app" -w /app node:22-alpine sh -c "npm ci --no-audit --no-fund && npm test"'
                    }
                }
                stage('ML Service') {
                    steps {
                        sh 'docker run --rm -e PYTHONDONTWRITEBYTECODE=1 -v "$WORKSPACE/ml-service:/app:ro" -w /app python:3.11-slim sh -c "pip install -q --no-cache-dir -r requirements.txt && python -m unittest -v test_main"'
                    }
                }
            }
        }

        stage('Build images') {
            steps {
                script {
                    withEnv(["BRANDFETCH_ID=${env.LIMITGUARD_BRANDFETCH_CLIENT_ID ?: ''}"]) {
                        SERVICES.each { svc ->
                            def image = "${env.REGISTRY}/${env.PROJECT}/${svc.name}"
                            def extra = svc.name == 'frontend' ? '--build-arg VITE_BRANDFETCH_CLIENT_ID="$BRANDFETCH_ID"' : ''
                            sh "docker build --pull --platform \"\$IMAGE_PLATFORM\" ${extra} -t ${image}:${env.IMAGE_TAG} -t ${image}:latest ${svc.dir}"
                        }
                    }
                }
            }
        }

        stage('Push to ECR') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'limitguard-aws',
                        usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh 'aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"'
                    script {
                        SERVICES.each { svc ->
                            def image = "${env.REGISTRY}/${env.PROJECT}/${svc.name}"
                            sh "docker push ${image}:${env.IMAGE_TAG} && docker push ${image}:latest"
                        }
                    }
                }
            }
        }

        stage('Deploy to EC2') {
            when { expression { params.DEPLOY } }
            steps {
                withCredentials([file(credentialsId: 'limitguard-env', variable: 'PROD_ENV')]) {
                    sshagent(credentials: ['limitguard-ec2-ssh']) {
                        sh '''
                            set -e
                            OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"
                            TARGET="ec2-user@$APP_HOST"

                            # 1. Copy the stack, the database file and the settings to the server.
                            ssh $OPTS "$TARGET" "mkdir -p /opt/limitguard/database /opt/limitguard/uploads"
                            scp $OPTS "$COMPOSE_FILE_PATH" "$TARGET:/opt/limitguard/compose.yaml"
                            scp $OPTS database/schema.sql "$TARGET:/opt/limitguard/database/schema.sql"
                            scp $OPTS "$PROD_ENV" "$TARGET:/opt/limitguard/.env"
                            ssh $OPTS "$TARGET" "chmod 600 /opt/limitguard/.env"

                            # 2. Run the deployment on the server.
                            ssh $OPTS "$TARGET" bash -s -- "$REGISTRY" "$IMAGE_TAG" "$AWS_REGION" <<'REMOTE'
set -euo pipefail
REGISTRY="$1"; IMAGE_TAG="$2"; AWS_REGION="$3"
cd /opt/limitguard
set -a; . ./.env; set +a
export REGISTRY IMAGE_TAG

echo "==> Logging in to ECR (server IAM role)"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

# psql from the official image; stdin is closed so it cannot read this script.
psql_on() {
  db="$1"; shift
  docker run --rm -e PGPASSWORD="$DB_PASSWORD" -e PGSSLMODE=require -v /opt/limitguard/database:/database:ro postgres:17 psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -U "${DB_USERNAME:-postgres}" -d "$db" "$@" < /dev/null
}

echo "==> Checking RDS"
if [ "$(psql_on postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'limitguard'")" != "1" ]; then
  echo "==> Creating database limitguard"
  psql_on postgres -c "CREATE DATABASE limitguard"
fi
if [ "$(psql_on limitguard -tAc "SELECT to_regclass('public.users') IS NOT NULL")" != "t" ]; then
  echo "==> First deploy: creating the tables and loading the data from schema.sql"
  psql_on limitguard -f /database/schema.sql > /dev/null
  echo "==> Loaded $(psql_on limitguard -tAc 'SELECT count(*) FROM users') users and $(psql_on limitguard -tAc 'SELECT count(*) FROM credit_requests') credit requests"
else
  echo "==> Database already set up; data left unchanged"
fi

echo "==> Starting LimitGuard $IMAGE_TAG"
docker compose --env-file .env -f compose.yaml pull < /dev/null
docker compose --env-file .env -f compose.yaml up -d --remove-orphans < /dev/null

for i in $(seq 1 40); do
  if curl -fsS -o /dev/null http://localhost/api/financial-institutions/directory; then
    echo "==> LimitGuard is up"
    docker image prune -f > /dev/null
    exit 0
  fi
  sleep 10
done
echo "ERROR: the app did not become ready. Recent logs:"
docker compose --env-file .env -f compose.yaml ps < /dev/null
docker compose --env-file .env -f compose.yaml logs --tail 60 auth-service credit-service api-gateway < /dev/null
exit 1
REMOTE
                        '''
                    }
                }
            }
        }

        stage('Smoke test') {
            when { expression { params.DEPLOY } }
            steps {
                sh '''
                    curl -fsS -o /dev/null -w "Frontend: HTTP %{http_code}\\n" "http://$APP_HOST/"
                    curl -fsS -o /dev/null -w "API:      HTTP %{http_code}\\n" "http://$APP_HOST/api/financial-institutions/directory"
                '''
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f >/dev/null 2>&1 || true'
        }
        success {
            echo "LimitGuard ${env.IMAGE_TAG} is live at http://${env.APP_HOST}/"
        }
    }
}
