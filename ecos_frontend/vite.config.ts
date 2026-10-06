/// <reference types="vitest" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      preserveSymlinks: true,
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      hmr: process.env.DISABLE_HMR !== 'true',
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      proxy: {
        // ── 引擎专属路由（优先级高于 Gateway fallback）──
        '/api/v1/agent-loop':    { target: 'http://localhost:18084', changeOrigin: true },
        // P0-3 修 (2026-10-06)：AgentMeshController 实际挂在 `/api/agent-mesh`（无 v1），
        // 前端统一发 `/api/v1/agent-mesh/*`。只有 gateway 的 VersionPrefixRewriteFilter
        // 会把 `/api/v1/agent-mesh/` 改写成 `/api/agent-mesh/`；standalone aiming :18084
        // 无该 rewrite filter → 原直连 404。改指 gateway :8080（与生产 BFF→gateway 一致），
        // gateway 实测 agent-mesh/agents=200。不越权改后端。
        '/api/v1/agent-mesh':    { target: 'http://localhost:8080', changeOrigin: true },
        '/api/v1/agent':         { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/agent-call':    { target: 'http://localhost:18084', changeOrigin: true },
        // 分册10 §5.1 W Agent 控制面新前缀（兄弟前缀，勿与 legacy /api/v1/agent 混用）
        '/api/v1/wagent':        { target: 'http://localhost:18084', changeOrigin: true },
        // 仅 dev 直连生效；生产一律走 BFF→gateway:8080。
        // 知识端点属主 = kb-engine（dccheng :18086），原误指 18084(ai-engine) 已订正（C92/W91）。
        '/api/v1/knowledge':     { target: 'http://localhost:18086', changeOrigin: true },
        '/api/v1/security':      { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/audit':         { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/abac':          { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/data-masking':  { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/data-permission':{ target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/policy-engine': { target: 'http://localhost:18081', changeOrigin: true },
        // P1-A 修 (2026-10-06)：DataEngineConfigController 在 datanet :18082 被 explicitly
        // exclude (PMO-49 P3-A)，settings/* 端点只在 gateway fat-jar 就活 → dev 改指 gateway。
        // 必须放在 /api/v1/engine/data 前（长前缀优先，避免被兜底规则截走）。
        '/api/v1/engine/data/settings': { target: 'http://localhost:8080', changeOrigin: true },
        '/api/v1/engine/data':   { target: 'http://localhost:18082', changeOrigin: true },
        // P1-B 修 (2026-10-06)：dq 数据质量规则端点属主 = data-engine (datanet :18082)。
        // 独立 buszhi :18083 不含 DqController → 404。改指 datanet，长前缀须先于 /api/v1/ecos。
        '/api/v1/ecos/dq':       { target: 'http://localhost:18082', changeOrigin: true },
        '/api/v1/ecos':          { target: 'http://localhost:18083', changeOrigin: true },
        // PMO-60: workspace routing + cognitive merged into aiming
        '/api/v1/workspace':     { target: 'http://localhost:18090', changeOrigin: true },
        '/api/v1/cognitive':     { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/world-model':   { target: 'http://localhost:18089', changeOrigin: true },
        '/api/v1/rules':         { target: 'http://localhost:18086', changeOrigin: true },
        '/api/v1/kb':            { target: 'http://localhost:18086', changeOrigin: true },
        // ── Fallback: Gateway ──
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              if (req.headers.authorization) {
                proxyReq.setHeader('Authorization', req.headers.authorization);
              }
            });
          },
        },
        '/datanet': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/cases': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      include: ['**/*.test.{ts,tsx}'],
    },
  };
});
