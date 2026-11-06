#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc \
    "$ROOT/app/src/main/java/ir/madreseyar/teacher/QuestionBankReview.kt" \
    "$ROOT/app/src/main/java/ir/madreseyar/teacher/TeacherUiHelpers.kt" \
    "$ROOT/app/src/main/java/ir/madreseyar/teacher/ApiPath.kt" \
    "$ROOT/app/src/main/java/ir/madreseyar/teacher/QuestionImagePath.kt" \
    "$ROOT/tools/local-smoke/JSONObjectStub.kt" \
    "$ROOT/tools/local-smoke/LocalSmoke.kt" \
    -include-runtime -d "$TMP/smoke.jar"
java -jar "$TMP/smoke.jar"
