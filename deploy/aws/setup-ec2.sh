#!/bin/bash
# =============================================================================
# KSP Workboard - EC2 Instance Setup Script
# =============================================================================
# RUN THIS SCRIPT AFTER SSH-ing INTO YOUR EC2 INSTANCE:
#   ssh -i ksp-key.pem ec2-user@<EC2-PUBLIC-IP>
#   bash setup-ec2.sh
# =============================================================================

set -e

echo "========================================="
echo "  KSP Workboard - EC2 Setup Starting"
echo "========================================="

# --- Step 1: System Updates ---
echo "[1/6] Updating system packages..."
sudo yum update -y

# --- Step 2: Install Java 17 ---
echo "[2/6] Installing Java 17 (Amazon Corretto)..."
sudo yum install -y java-17-amazon-corretto-devel
java -version

# --- Step 3: Install PostgreSQL client (for connecting to RDS) ---
echo "[3/6] Installing PostgreSQL client..."
sudo yum install -y postgresql16

# --- Step 4: Create application directory ---
echo "[4/6] Creating application directories..."
sudo mkdir -p /opt/ksp/logs
sudo chown -R ec2-user:ec2-user /opt/ksp

# --- Step 5: Install CloudWatch Agent (for monitoring) ---
echo "[5/6] Installing CloudWatch agent..."
sudo yum install -y amazon-cloudwatch-agent

# --- Step 6: Install Git (for pulling code updates) ---
echo "[6/6] Installing Git..."
sudo yum install -y git

echo "========================================="
echo "  EC2 Setup Complete!"
echo "========================================="
echo ""
echo "NEXT STEPS:"
echo "  1. Upload your JAR file:"
echo "     scp -i ksp-key.pem backend/build/libs/ksp-workboard-0.0.1-SNAPSHOT.jar ec2-user@<IP>:/opt/ksp/ksp-workboard.jar"
echo ""
echo "  2. Copy the systemd service file:"
echo "     scp -i ksp-key.pem backend/deploy/aws/ksp-backend.service ec2-user@<IP>:~/"
echo "     Then on EC2: sudo cp ~/ksp-backend.service /etc/systemd/system/"
echo ""
echo "  3. Edit the service file with your actual values:"
echo "     sudo vi /etc/systemd/system/ksp-backend.service"
echo ""
echo "  4. Start the service:"
echo "     sudo systemctl daemon-reload"
echo "     sudo systemctl enable ksp-backend"
echo "     sudo systemctl start ksp-backend"
echo ""
echo "  5. Check logs:"
echo "     sudo journalctl -u ksp-backend -f"
echo ""
