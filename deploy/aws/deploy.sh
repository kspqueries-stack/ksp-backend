#!/bin/bash
# =============================================================================
# KSP Workboard - One-Command Deploy Script
# =============================================================================
# USAGE (from project root, in Git Bash):
#   bash backend/deploy/aws/deploy.sh <EC2-PUBLIC-IP>
#
# WHAT IT DOES:
#   1. Builds the Spring Boot JAR
#   2. Uploads it to EC2
#   3. Restarts the backend service
#   4. Builds the frontend
#   5. Uploads to S3
#   6. Invalidates CloudFront cache
#
# PREREQUISITES:
#   - ksp-key.pem in your home directory
#   - AWS CLI configured (aws configure)
#   - Node.js and npm installed
#   - Java 17+ installed
# =============================================================================

set -e

# --- Configuration (UPDATE THESE ONCE) ---
EC2_IP="${1:?Usage: deploy.sh <EC2-PUBLIC-IP>}"
KEY_PATH="$HOME/ksp-key.pem"
EC2_USER="ec2-user"
S3_BUCKET="ksp-frontend-bucket"
CLOUDFRONT_DIST_ID="E3BFBLGQL4FPA3"

# --- Derived paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
JAR_NAME="ksp-workboard-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BACKEND_DIR/build/libs/$JAR_NAME"

# --- Colors ---
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  KSP Workboard - Full Deploy${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""

# --- Step 1: Build Backend ---
echo -e "${YELLOW}[1/6] Building backend JAR...${NC}"
cd "$BACKEND_DIR"
./gradlew bootJar -x test --quiet
echo "  ✓ JAR built: $JAR_PATH"

# --- Step 2: Upload JAR to EC2 ---
echo -e "${YELLOW}[2/6] Uploading JAR to EC2 ($EC2_IP)...${NC}"
scp -i "$KEY_PATH" -o StrictHostKeyChecking=no "$JAR_PATH" "$EC2_USER@$EC2_IP:/opt/ksp/ksp-workboard.jar"
echo "  ✓ JAR uploaded"

# --- Step 3: Restart backend service ---
echo -e "${YELLOW}[3/6] Restarting backend service...${NC}"
ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "$EC2_USER@$EC2_IP" "sudo systemctl restart ksp-backend"
echo "  ✓ Service restarted"

# --- Step 4: Build Frontend ---
echo -e "${YELLOW}[4/6] Building frontend...${NC}"
cd "$FRONTEND_DIR"
npm run build --silent
echo "  ✓ Frontend built"

# --- Step 5: Upload to S3 ---
echo -e "${YELLOW}[5/6] Syncing frontend to S3...${NC}"
aws s3 sync dist/ "s3://$S3_BUCKET/" --delete --quiet
echo "  ✓ S3 synced"

# --- Step 6: Invalidate CloudFront ---
echo -e "${YELLOW}[6/6] Invalidating CloudFront cache...${NC}"
aws cloudfront create-invalidation --distribution-id "$CLOUDFRONT_DIST_ID" --paths "/*" > /dev/null 2>&1
echo "  ✓ Cache invalidated"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  ✓ Deployment Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "  Backend:  http://$EC2_IP:8080/api/health"
echo "  Frontend: Check your CloudFront URL"
echo ""

# --- Quick health check ---
echo -e "${YELLOW}Waiting 10s for backend to start...${NC}"
sleep 10
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://$EC2_IP:8080/api/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}  ✓ Backend is healthy!${NC}"
else
    echo -e "${RED}  ⚠ Backend returned HTTP $HTTP_CODE (might still be starting)${NC}"
    echo "  Run: ssh -i $KEY_PATH $EC2_USER@$EC2_IP 'sudo journalctl -u ksp-backend -n 30'"
fi
