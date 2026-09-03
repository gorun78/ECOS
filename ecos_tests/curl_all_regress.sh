#!/usr/bin/env bash
# ECOS 591 端点 curl_all 回归 (Wave-5.2 T22 - 守裴 G4)
# 用法: bash curl_all_regress.sh [--sample N]   (默认全跑)
# 守: 591 端点 0 5xx + 0 neterr 即 G4 GO
# 注: Wave-4.2 遗留 P0 - 1 波 entity-link 403 不算 FAIL (PLAN P0 遗留, 本波不 fix)
set -uo pipefail

unset PATH
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

WF_DIR="$(dirname "$(readlink -f "$0")")"
python3 "$WF_DIR/curl_all_regress.py" "$@"
