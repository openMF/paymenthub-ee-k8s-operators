#!/usr/bin/env bash
# Build a local Docker image and load it into the k3s containerd runtime.
# Does not push to any registry. Docker Hub credentials are not required.
# Usage: ./build-local-image.sh [-t]   (-t runs tests before building; skipped by default)
set -euo pipefail

RUN_TESTS=false
while getopts "t" opt; do
    case "$opt" in
        t) RUN_TESTS=true ;;
        *) echo "Usage: $0 [-t]"; exit 1 ;;
    esac
done

# Detect CPU architecture
case "$(uname -m)" in
    x86_64)        ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *) echo "Unsupported architecture: $(uname -m)"; exit 1 ;;
esac

# Detect k3s platform: Colima (macOS) or native Linux
if command -v colima &>/dev/null && colima status 2>/dev/null | grep -q "running"; then
    PLATFORM="mac"
else
    PLATFORM="linux"
fi

IMAGE="paymenthub-operator:local"

echo "==> Platform: ${PLATFORM}, arch: linux/${ARCH}"

echo "==> Building jar (tests $([ "$RUN_TESTS" = true ] && echo enabled || echo skipped))..."
if [ "$RUN_TESTS" = true ]; then
    ./gradlew build
else
    ./gradlew build -x test
fi

echo "==> Building Docker image ${IMAGE} for linux/${ARCH}..."
docker build --platform "linux/${ARCH}" -t "${IMAGE}" .

echo "==> Loading ${IMAGE} into k3s containerd..."
if [ "${PLATFORM}" = "mac" ]; then
    docker save "${IMAGE}" | colima ssh -- sudo k3s ctr images import -
else
    docker save "${IMAGE}" | sudo k3s ctr images import -
fi

echo ""
echo "==> Done. Use in a pod/deployment spec:"
echo "      image: ${IMAGE}"
echo "      imagePullPolicy: Never"
