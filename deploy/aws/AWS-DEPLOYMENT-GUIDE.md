# KSP Workboard - AWS Deployment Guide

## Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   CloudFront    │────▶│   S3 Bucket      │     │  AWS Bedrock    │
│  (CDN + HTTPS)  │     │  (Frontend SPA)  │     │ (Amazon Nova    │
└────────┬────────┘     └──────────────────┘     │  Lite AI)       │
         │                                        └────────▲────────┘
         │ API requests (/api/*)                            │
         ▼                                                  │
┌─────────────────┐                               ┌────────┴────────┐
│   EC2 Instance  │───────────────────────────────▶│      RDS        │
│  (Spring Boot)  │                                │  (PostgreSQL    │
│  t3.medium      │                                │   + pgvector)   │
└─────────────────┘                                └─────────────────┘
```

## Profiles

| Profile | When Used | AI Provider |
|---------|-----------|-------------|
| `local` | Your laptop (`./gradlew bootRun`) | Groq (free tier) |
| `prod`  | AWS EC2 (`--spring.profiles.active=prod`) | AWS Bedrock (Nova Lite) |

## Deploy Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| `deploy.sh` | Full deploy (backend + frontend) | `bash backend/deploy/aws/deploy.sh <EC2-IP>` |
| `deploy-backend.sh` | Backend only (Java changes) | `bash backend/deploy/aws/deploy-backend.sh <EC2-IP>` |
| `deploy-frontend.sh` | Frontend only (UI changes) | `bash backend/deploy/aws/deploy-frontend.sh` |

## Cost Estimate (Monthly)

| Service | Size | Est. Cost |
|---------|------|-----------|
| EC2 (t3.medium) | 2 vCPU, 4GB RAM | ~$30/month |
| RDS (db.t3.micro) | 1 vCPU, 1GB RAM | Free tier / ~$15 |
| S3 + CloudFront | Static frontend | ~$1-2 |
| Bedrock (Nova Lite) | Per-token | ~$5-30 (usage dependent) |
| **Total** | | **~$50-80/month** |

## Placeholders You MUST Replace

### In `ksp-backend.service`:

| Placeholder | Where to Find | Example |
|-------------|---------------|---------|
| `<RDS-ENDPOINT>` | RDS → ksp-postgres → Connectivity | `ksp-postgres.cxyz.us-east-1.rds.amazonaws.com` |
| `<YOUR-RDS-PASSWORD>` | Password you set during RDS creation | `MyStr0ngP@ss!` |
| `<GENERATE-WITH-openssl-rand-base64-48>` | Run: `openssl rand -base64 48` | `aB3kF9mPqR7sT2v...` |
| `<YOUR-CLOUDFRONT-DOMAIN>` | CloudFront → Distribution → Domain | `d1a2b3c4e5f.cloudfront.net` |

### In `deploy.sh` and `deploy-frontend.sh`:

| Placeholder | Where to Find | Example |
|-------------|---------------|---------|
| `<YOUR-S3-BUCKET-NAME>` | S3 → your bucket name | `ksp-frontend-bucket` |
| `<YOUR-CLOUDFRONT-ID>` | CloudFront → Distribution → ID | `E1A2B3C4D5F6G7` |

### In `frontend/.env.production`:

| Placeholder | Where to Find | Example |
|-------------|---------------|---------|
| `<YOUR-EC2-PUBLIC-IP>` | EC2 → Instances → Public IPv4 | `54.123.45.67` |

---

## Remaining AWS Steps (You've done Phase 1-5 already)

### What's Done ✅
- AWS account created with MFA
- IAM admin user (ksp-admin)
- Budget alerts configured
- VPC created (ksp-vpc with 2 public + 2 private subnets)
- RDS PostgreSQL instance (ksp-postgres)
- pgvector extension enabled
- EC2 IAM role (ksp-ec2-role)
- Security groups (ksp-backend-sg, ksp-db-sg)
- Key pair (ksp-key)
- EC2 instance launched (ksp-backend)

### What's Next (Steps we'll do together)
1. **Install AWS CLI on your laptop** (for deploying)
2. **SSH into EC2 and run setup script** (one-time)
3. **Create S3 bucket for frontend**
4. **Create CloudFront distribution**
5. **Enable Bedrock model access**
6. **Configure and deploy**
