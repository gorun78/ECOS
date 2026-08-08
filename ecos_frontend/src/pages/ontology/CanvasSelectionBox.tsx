/**
 * CanvasSelectionBox — Shift+拖拽框选矩形
 *
 * 在 SVG 画布上按住 Shift 并拖拽时显示矩形选区。
 * 由 DomainCanvas 控制何时渲染（仅当拖拽中且 Shift 按下）。
 * 不管理自己的拖拽逻辑，仅接收坐标参数渲染矩形。
 *
 * @license Apache-2.0
 */

import React from 'react';

// ── Props ──────────────────────────────────────────────────────

export interface CanvasSelectionBoxProps {
  /** 选区左上角 x */
  x1: number;
  /** 选区左上角 y */
  y1: number;
  /** 选区右下角 x */
  x2: number;
  /** 选区右下角 y */
  y2: number;
}

// ── 组件 ──────────────────────────────────────────────────────

export default function CanvasSelectionBox({
  x1,
  y1,
  x2,
  y2,
}: CanvasSelectionBoxProps) {
  const x = Math.min(x1, x2);
  const y = Math.min(y1, y2);
  const w = Math.abs(x2 - x1);
  const h = Math.abs(y2 - y1);

  // 拖拽距离太短时不显示
  if (w < 3 || h < 3) return null;

  return (
    <rect
      x={x}
      y={y}
      width={w}
      height={h}
      fill="rgba(99, 102, 241, 0.08)"
      stroke="#6366f1"
      strokeWidth={1}
      strokeDasharray="4 3"
      rx={2}
      pointerEvents="none"
    />
  );
}

// ── 辅助：判断实体是否在选区内 ──────────────────────────────

export function isEntityInSelection(
  entityCenterX: number,
  entityCenterY: number,
  selX1: number,
  selY1: number,
  selX2: number,
  selY2: number,
): boolean {
  const left = Math.min(selX1, selX2);
  const right = Math.max(selX1, selX2);
  const top = Math.min(selY1, selY2);
  const bottom = Math.max(selY1, selY2);

  return (
    entityCenterX >= left &&
    entityCenterX <= right &&
    entityCenterY >= top &&
    entityCenterY <= bottom
  );
}
