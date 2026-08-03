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
        '/api/v1/agent-mesh':    { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/agent':         { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/agent-call':    { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/knowledge':     { target: 'http://localhost:18084', changeOrigin: true },
        '/api/v1/security':      { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/audit':         { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/abac':          { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/data-masking':  { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/data-permission':{ target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/policy-engine': { target: 'http://localhost:18081', changeOrigin: true },
        '/api/v1/engine/data':   { target: 'http://localhost:18082', changeOrigin: true },
        '/api/v1/ecos':          { target: 'http://localhost:18083', changeOrigin: true },
        '/api/v1/cognitive':     { target: 'http://localhost:18089', changeOrigin: true },
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
