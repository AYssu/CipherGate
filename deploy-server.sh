#!/usr/bin/env bash
set -euo pipefail

BUNDLE_DIR="deploy-bundle"
ZIP_NAME="ciphergate-deploy.zip"

info()  { echo -e "\033[1;32m[INFO]\033[0m $*"; }
error() { echo -e "\033[1;31m[ERROR]\033[0m $*"; exit 1; }

# ---------- 1. Build backend JAR ----------
info "[1/7] Build backend JAR..."
./gradlew clean bootJar --no-daemon

# ---------- 2. Build crypto plugins ----------
info "[2/7] Build crypto plugins..."
./gradlew -p plugins/rsa-crypto-plugin clean jar --no-daemon

# ---------- 3. Build frontend dist ----------
info "[3/7] Build frontend dist..."
pushd frontend > /dev/null
if [ ! -d "node_modules" ]; then
  npm install --include=dev --no-audit --no-fund
fi
if [ ! -f "node_modules/.bin/tsc" ]; then
  info "[INFO] typescript not found, reinstalling dev dependencies..."
  npm install --include=dev --no-audit --no-fund
fi
npm run build
popd > /dev/null

# ---------- 4. Build docs ----------
info "[4/7] Build docs..."
pushd docs > /dev/null
if [ ! -d "node_modules" ]; then
  npm install --no-audit --no-fund
fi
npm run build
popd > /dev/null

# ---------- 5. Prepare bundle ----------
info "[5/7] Prepare bundle..."
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/app" "$BUNDLE_DIR/plugins" "$BUNDLE_DIR/frontend" "$BUNDLE_DIR/docs"

# 找到最新的非 plain jar
JAR_FILE=$(ls -t build/libs/*.jar 2>/dev/null | grep -v '\-plain\.jar$' | head -1)
if [ -z "$JAR_FILE" ]; then
  error "No runnable bootJar found in build/libs"
fi
info "Using jar: $JAR_FILE"
cp "$JAR_FILE" "$BUNDLE_DIR/app/app.jar"

cp -r frontend/dist "$BUNDLE_DIR/frontend/dist"
cp frontend/nginx.conf "$BUNDLE_DIR/frontend/nginx.conf"
cp docker-compose.server.yml "$BUNDLE_DIR/docker-compose.server.yml"

# 复制文档
if [ -d "docs/.vitepress/dist" ]; then
  cp -r docs/.vitepress/dist/* "$BUNDLE_DIR/docs/"
  info "Docs bundled."
else
  echo -e "\033[1;33m[WARN]\033[0m docs/.vitepress/dist not found, skipping docs."
fi

# Copy plugin JARs
PLUGIN_COUNT=0
for jar in plugins/*/build/libs/*.jar; do
  [ -f "$jar" ] || continue
  cp "$jar" "$BUNDLE_DIR/plugins/$(basename "$jar")"
  info "Plugin: $(basename "$jar")"
  PLUGIN_COUNT=$((PLUGIN_COUNT + 1))
done
if [ "$PLUGIN_COUNT" -eq 0 ]; then
  echo -e "\033[1;33m[WARN]\033[0m No plugin JARs found in plugins/*/build/libs/"
else
  info "$PLUGIN_COUNT plugin(s) bundled."
fi

# 检查 .env.server
if [ ! -f ".env.server" ]; then
  error "Missing .env.server. Copy from .env.server.example and edit it."
fi
cp .env.server "$BUNDLE_DIR/.env"

# 随机化宿主机端口（20000-49999）
info "[INFO] Randomizing host ports in bundle .env ..."
random_port() {
  local min=$1 max=$2
  while :; do
    local port=$(( RANDOM % (max - min + 1) + min ))
    # 检查端口是否已被占用（简单检查）
    if ! ss -tlnH 2>/dev/null | grep -q ":${port} "; then
      echo "$port"
      return
    fi
  done
}

PORTS=(
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
  "$(random_port 20000 49999)"
)

ENV_FILE="$BUNDLE_DIR/.env"
sed -i "s/^MYSQL_PORT=.*/MYSQL_PORT=${PORTS[0]}/"       "$ENV_FILE"
sed -i "s/^REDIS_PORT_HOST=.*/REDIS_PORT_HOST=${PORTS[1]}/" "$ENV_FILE"
sed -i "s/^MINIO_API_PORT=.*/MINIO_API_PORT=${PORTS[2]}/"   "$ENV_FILE"
sed -i "s/^MINIO_CONSOLE_PORT=.*/MINIO_CONSOLE_PORT=${PORTS[3]}/" "$ENV_FILE"
sed -i "s/^BACKEND_PORT=.*/BACKEND_PORT=${PORTS[4]}/"     "$ENV_FILE"
sed -i "s/^FRONTEND_PORT=.*/FRONTEND_PORT=${PORTS[5]}/"   "$ENV_FILE"
sed -i "s/^RABBITMQ_PORT=.*/RABBITMQ_PORT=${PORTS[6]}/"   "$ENV_FILE"
sed -i "s/^RABBITMQ_MGMT_PORT=.*/RABBITMQ_MGMT_PORT=${PORTS[7]}/" "$ENV_FILE"

info "  MYSQL_PORT=${PORTS[0]}"
info "  REDIS_PORT_HOST=${PORTS[1]}"
info "  MINIO_API_PORT=${PORTS[2]}"
info "  MINIO_CONSOLE_PORT=${PORTS[3]}"
info "  BACKEND_PORT=${PORTS[4]}"
info "  FRONTEND_PORT=${PORTS[5]}"
info "  RABBITMQ_PORT=${PORTS[6]}"
info "  RABBITMQ_MGMT_PORT=${PORTS[7]}"

# ---------- 6. Create zip ----------
info "[6/7] Create zip package..."
rm -f "$ZIP_NAME"
(cd "$BUNDLE_DIR" && zip -r -q "../$ZIP_NAME" .)

# ---------- 7. Done ----------
info "[7/7] Done."
echo ""
echo "  Bundle folder : $BUNDLE_DIR"
echo "  Zip package   : $ZIP_NAME"
echo ""
echo "  Upload one of them to server, then run:"
echo "    docker compose -f docker-compose.server.yml up -d"
