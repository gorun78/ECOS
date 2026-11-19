#!/usr/bin/env bash
# =============================================================================
# Controller 骨架生成器（治理建议 §三 第 4 件）
# -----------------------------------------------------------------------------
# 背景：铁律 §1.2 自标「最高频踩坑」——新增 Controller 必须三处接线
#   (1) VersionPrefixRewriteFilter  版本前缀重写
#   (2) SecurityConfig.permitAll    匿名白名单
#   (3) ClearanceInterceptor        准入等级豁免
# 漏一处的后果是 403/404，且编译期无感。本脚本生成 Controller 骨架后
# **立即调用 check-controller-filter.sh 自检**，把"记住三处"变成"生成即校验"。
#
# 用法：
#   bash ecos_backend/scripts/new-controller.sh \
#     --module services/datanet/datanet-impl \
#     --package com.chinacreator.gzcm.datanet.controller \
#     --class FooController --path /api/v1/foo
#
# 退出码：0 = 已生成且三滤波器自检通过；1 = 自检未过或目标已存在；2 = 参数错误
# =============================================================================

set -eu

MODULE=""
PKG=""
CLASS=""
PATH_=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --module) MODULE="$2"; shift 2 ;;
    --package) PKG="$2"; shift 2 ;;
    --class) CLASS="$2"; shift 2 ;;
    --path) PATH_="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$MODULE" ] || [ -z "$PKG" ] || [ -z "$CLASS" ] || [ -z "$PATH_" ]; then
  echo "用法: bash $(basename "$0") --module <相对路径> --package <包名> --class <类名> --path </api/v1/xxx>" >&2
  exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="${ROOT_DIR}/ecos_backend/${MODULE}/src/main/java/$(echo "$PKG" | tr '.' '/')"
TARGET="${SRC_DIR}/${CLASS}.java"

if [ -e "$TARGET" ]; then
  echo "[FATAL] 目标已存在，拒绝覆盖: $TARGET" >&2
  exit 1
fi

mkdir -p "$SRC_DIR"

cat > "$TARGET" <<EOF
package ${PKG};

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.api.ApiResponse;

/**
 * ${CLASS} — 由 new-controller.sh 生成。
 *
 * <p><b>三滤波器接线（铁律 §1.2，漏一处 = 403/404 且编译期无感）</b>：
 * 生成后须在下列三处补齐本 Controller 暴露的路径，再跑
 * {@code bash ecos_backend/scripts/check-controller-filter.sh "${PATH_}"} 自检：
 * <ol>
 *   <li>VersionPrefixRewriteFilter（gateway/filter）— 版本前缀重写</li>
 *   <li>SecurityConfig.permitAll（sysman/security）— 匿名白名单（仅当端点应匿名）</li>
 *   <li>ClearanceInterceptor（sysman/security）— 准入等级豁免</li>
 * </ol>
 * 业务数据端点一般<b>不应</b>加 permitAll（鉴权由 gateway 统一裁决）。</p>
 */
@RestController
@RequestMapping("${PATH_}")
public class ${CLASS} {

    @GetMapping("/_health")
    public ApiResponse<java.util.Map<String, String>> health() {
        return ApiResponse.success(java.util.Map.of("status", "UP"));
    }
}
EOF

echo "[OK] 已生成: $TARGET"
echo "[NEXT] 补三滤波器后跑自检："

set +e
bash "${ROOT_DIR}/ecos_backend/scripts/check-controller-filter.sh" "${PATH_}"
RC=$?
set -e
exit "$RC"
