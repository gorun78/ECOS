/**
 * PMO-48 — Data Quality governance API barrel
 * (从原单文件 data-quality/api.ts 拆分为子模块, 见 W66 前端 <800 行约定)
 * 既有 `from "./api"` 导入面无变化 —— Vite/bundler 解析到本目录 index.ts。
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */
export * from "./_base";
export * from "./types";
export * from "./rules";
export * from "./scores-schedules-alerts";
