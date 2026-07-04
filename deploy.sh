#!/bin/bash
# 로컬(개발 PC)에서 실행하는 운영 재배포 스크립트.
#
# 전제: 배포할 변경사항이 이미 git commit 되어 있어야 한다(git archive 는 워킹트리가
# 아니라 커밋을 내보낸다 — 커밋 안 한 변경은 배포되지 않는다).
#
# 사용법:
#   ./deploy.sh          # HEAD(현재 브랜치 최신 커밋) 배포
#   ./deploy.sh <ref>    # 특정 커밋/태그 배포
#
# 서버 쪽에서 절대 건드리지 않는 경로(실 데이터·시크릿 — 코드 배포와 분리):
#   .env.production, pgdata/(운영 DB 데이터), backups/, backup.log, backup.sh
set -euo pipefail

REF="${1:-HEAD}"
SERVER="root@49.247.139.234"
REMOTE_DIR="/root/seller-profit"
REMOTE_TMP="/root/seller-profit-deploy-tmp"
LOCAL_TAR="/tmp/seller-profit-deploy-$$.tar.gz"

cleanup() { rm -f "${LOCAL_TAR}"; }
trap cleanup EXIT

echo "==> ${REF} 커밋을 tar 로 내보내는 중..."
git archive --format=tar.gz -o "${LOCAL_TAR}" "${REF}"

echo "==> 서버로 전송 중..."
scp "${LOCAL_TAR}" "${SERVER}:${REMOTE_TMP}.tar.gz"

echo "==> 서버에서 코드만 갱신(운영 데이터/시크릿 보존) 후 재빌드..."
ssh "${SERVER}" bash -s <<REMOTE
set -euo pipefail
rm -rf "${REMOTE_TMP}"
mkdir -p "${REMOTE_TMP}"
tar -xzf "${REMOTE_TMP}.tar.gz" -C "${REMOTE_TMP}"
rm -f "${REMOTE_TMP}.tar.gz"
rsync -a --delete \
  --exclude='.env.production' \
  --exclude='pgdata/' \
  --exclude='backups/' \
  --exclude='backup.log' \
  --exclude='backup.sh' \
  "${REMOTE_TMP}/" "${REMOTE_DIR}/"
rm -rf "${REMOTE_TMP}"
cd "${REMOTE_DIR}"
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.production -f docker-compose.prod.yml ps
REMOTE

echo "==> 배포 완료. https://sellerprofit.co.kr/ 확인하세요."
