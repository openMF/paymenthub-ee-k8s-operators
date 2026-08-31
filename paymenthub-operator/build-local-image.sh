#!/usr/bin/env bash
# Build the paymenthub-operator jar and image, then load it into the local
# k3s (or Colima) cluster for dev/test. Does not push to any registry.
# Usage: ./build-local-image.sh [-t]   (-t runs tests before building; skipped by default)
#
# The image build + cluster import is delegated to mifos-gazelle's
# build-and-import-image.sh so this repo doesn't carry its own copy of
# "build with buildx, detect arch, load into k3s/Colima". Set GAZELLE_DIR if
# mifos-gazelle isn't checked out as a sibling of this repo.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GAZELLE_DIR="${GAZELLE_DIR:-$SCRIPT_DIR/../../mifos-gazelle}"
BUILD_SCRIPT="$GAZELLE_DIR/src/utils/build-and-import-image.sh"

RUN_TESTS=false
while getopts "t" opt; do
    case "$opt" in
        t) RUN_TESTS=true ;;
        *) echo "Usage: $0 [-t]"; exit 1 ;;
    esac
done

[[ -x "$BUILD_SCRIPT" ]] || {
    echo "ERROR: mifos-gazelle's build-and-import-image.sh not found at $BUILD_SCRIPT" >&2
    echo "Check out mifos-gazelle as a sibling of this repo, or set GAZELLE_DIR to point at your checkout." >&2
    exit 1
}

IMAGE="paymenthub-operator"
TAG="local"

echo "==> Building jar (tests $([ "$RUN_TESTS" = true ] && echo enabled || echo skipped))..."
if [ "$RUN_TESTS" = true ]; then
    ./gradlew build
else
    ./gradlew build -x test
fi

echo "==> Building and importing ${IMAGE}:${TAG} via $BUILD_SCRIPT"
"$BUILD_SCRIPT" -n "$IMAGE" -t "$TAG" -c "$SCRIPT_DIR"

echo ""
echo "==> Done. Use in a pod/deployment spec:"
echo "      image: ${IMAGE}:${TAG}"
echo "      imagePullPolicy: Never"
