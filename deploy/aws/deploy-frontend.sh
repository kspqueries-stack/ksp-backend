#!/bin/bash
# =============================================================================
# KSP Workboard - Frontend-Only Deploy (faster for UI-only changes)
# =============================================================================
# USAGE: bash backend/deploy/aws/deploy-frontend.sh
# =============================================================================

set -e

S3_BUCKET="ksp-frontend-bucket"
CLOUDFRONT_DIST_ID="E3BFBLGQL4FPA3"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}[1/3] Building frontend...${NC}"
cd "$FRONTEND_DIR"
npm run build --silent

echo -e "${YELLOW}[2/3] Syncing to S3...${NC}"
aws s3 sync dist/ "s3://$S3_BUCKET/" --delete --quiet

echo -e "${YELLOW}[3/3] Invalidating CloudFront cache...${NC}"
aws cloudfront create-invalidation --distribution-id "$CLOUDFRONT_DIST_ID" --paths "/*" > /dev/null 2>&1

echo -e "${GREEN}✓ Frontend deployed!${NC}"
