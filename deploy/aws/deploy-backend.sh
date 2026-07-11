#!/bin/bash
# =============================================================================
# KSP Workboard - Backend-Only Deploy (faster for Java-only changes)
# =============================================================================
# USAGE: bash backend/deploy/aws/deploy-backend.sh <EC2-PUBLIC-IP>
# =============================================================================

set -e

EC2_IP="${1:?Usage: deploy-backend.sh <EC2-PUBLIC-IP>}"
KEY_PATH="$HOME/ksp-key.pem"
EC2_USER="ec2-user"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
JAR_PATH="$BACKEND_DIR/build/libs/ksp-workboard-0.0.1-SNAPSHOT.jar"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}[1/3] Building backend...${NC}"
cd "$BACKEND_DIR"
./gradlew bootJar -x test --quiet

echo -e "${YELLOW}[2/3] Uploading to EC2...${NC}"
scp -i "$KEY_PATH" -o StrictHostKeyChecking=no "$JAR_PATH" "$EC2_USER@$EC2_IP:/opt/ksp/ksp-workboard.jar"

echo -e "${YELLOW}[3/3] Restarting service...${NC}"
ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "$EC2_USER@$EC2_IP" "sudo systemctl restart ksp-backend"

echo -e "${GREEN}✓ Backend deployed! Checking health in 10s...${NC}"
sleep 10
curl -s "http://$EC2_IP:8080/api/health" && echo ""
