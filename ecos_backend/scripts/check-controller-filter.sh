#!/usr/bin/env bash
# =============================================================================
# 三滤波器静态一致性校验脚本（架构铁律 §1.2 自动挡）
# -----------------------------------------------------------------------------
# 用途：新增 Controller 后，校验其暴露的每个访问路径是否在下列三处都完成配置：
#   1) VersionPrefixRewriteFilter  (gateway/filter)  — 版本前缀重写映射
#   2) SecurityConfig               (sysman/security) — permitAll 白名单
#   3) ClearanceInterceptor         (sysman/security) — 准入等级豁免列表
# 任一路径任一处缺失 → 非 0 退出码（403/404 高危，铁律标注 🔴 最高频踩坑）
#
# 用法：
#   bash check-controller-filter.sh "/api/v1/datasource" "/datasource" "/api/datasource"
#   （参数为该 Controller 实际暴露的完整访问路径前缀，可含或不含末尾 "/"）
#
# 退出码：
#   0 = 全部路径三处齐备；1 = 存在缺失项
# =============================================================================

set -u

# 项目根：脚本位于 ecos_backend/scripts/，向上两级 = ECOS 根
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

FILTER_FILE="${ROOT_DIR}/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java"
SECURITY_FILE="${ROOT_DIR}/ecos_backend/sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java"
INTERCEPTOR_FILE="${ROOT_DIR}/ecos_backend/sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java"

if [ "$#" -lt 1 ]; then
  echo "用法: bash $(basename "$0") \"/api/v1/<prefix>\" [\"/<prefix>\" ...]" >&2
  echo "示例: bash $(basename "$0") \"/api/v1/datasource\" \"/datasource\"" >&2
  exit 2
fi

# 校验三个源文件存在
for f in "${FILTER_FILE}" "${SECURITY_FILE}" "${INTERCEPTOR_FILE}"; do
  if [ ! -f "$f" ]; then
    echo "[FATAL] 文件不存在: $f" >&2
    exit 2
  fi
done

# 对每个路径在三处逐一 grep（字面前缀匹配）
fail_count=0
for path in "$@"; do
  # 去掉末尾 "/"，前缀字面量匹配即可命中 /prefix、/prefix/、/prefix/** 三种实际写法
  prefix="${path%/}"

  echo "---- 路径: ${path} ----"

  # 1) 重写 Filter
  if grep -qF -e "${prefix}" "${FILTER_FILE}"; then
    echo "  [PASS] VersionPrefixRewriteFilter"
  else
    echo "  [FAIL] VersionPrefixRewriteFilter  (缺 ${prefix} 重写/反向规则)"
    fail_count=$((fail_count + 1))
  fi

  # 2) SecurityConfig permitAll
  if grep -qF -e "${prefix}" "${SECURITY_FILE}"; then
    echo "  [PASS] SecurityConfig.permitAll"
  else
    echo "  [FAIL] SecurityConfig.permitAll  (缺 ${prefix}/** 白名单)"
    fail_count=$((fail_count + 1))
  fi

  # 3) ClearanceInterceptor 豁免
  if grep -qF -e "${prefix}" "${INTERCEPTOR_FILE}"; then
    echo "  [PASS] ClearanceInterceptor"
  else
    echo "  [FAIL] ClearanceInterceptor  (缺 ${prefix} 豁免)"
    fail_count=$((fail_count + 1))
  fi
done

echo ""
if [ "${fail_count}" -gt 0 ]; then
  echo "[RESULT] FAIL — ${fail_count} 处缺失，禁止交付（先补三滤波器再验收）"
  exit 1
else
  echo "[RESULT] PASS — 所有路径三处齐备"
  exit 0
fi