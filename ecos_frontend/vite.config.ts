/// <reference types="vitest" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';
import {
  AIMING_DEV_TRUST_HEADERS,
  buildViteDevProxyTable,
  validateRouteManifest,
} from './config/route-manifest';

// route-manifest 单源（详细设计-08 §4.1 / W194/C176 / R-54 ①）：
// dev 代理表由 config/route-manifest.ts 派生（18 条引擎直连 + 3 条 gateway 宿主的 knowledge 子路径
// + /api、/datanet、/cases 三条 gateway 兜底 = 24 条），
// 与本文件同源的 server.ts（Express BFF）读取同一清单；构建期校验失败即抛错，禁运行期静默。
const devProxyTable = buildViteDevProxyTable(process.env);
const manifestErrors = validateRouteManifest(undefined, devProxyTable);
if (manifestErrors.length > 0) {
  throw new Error(
    `[vite.config][route-manifest] 路由表单源校验失败:\n - ${manifestErrors.join('\n - ')}`,
  );
}
// F05-14/R-16: aiming service 态启用 X-ECOS-* 信任头 fail-closed 校验（ecos.security.header-auth）。
// vite dev 直连 :18084 属受信内网 dev 通道，注入 dev 信任头等价模拟 gateway 下行注入；
// 生产无 vite 路径（Express BFF → gateway :8080），未登录访问仍被拒绝。
void AIMING_DEV_TRUST_HEADERS; // 信任头由 buildViteDevProxyTable 内部注入，此处仅保留口径注释。

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
      proxy: devProxyTable,
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      // design-05 F05-17 验收用例文件名用 .spec.ts（vitest 默认只收 .test.*，此处显式补 .spec.*）
      include: ['**/*.test.{ts,tsx}', 'src/**/*.spec.{ts,tsx}'],
      // 详细设计-08 W198/C180：覆盖率门槛（vitest 4 在 vite.config.test 内配置即等效独立 vitest.config.ts，
      // 避免双配置文件漂移；npm test 语义不变——coverage 仅在显式 --coverage 时生效）。
      coverage: {
        provider: 'v8' as const,
        reporter: ['text', 'json-summary'],
        reportsDirectory: './coverage',
        // 门槛只加不减：低于 floor 即 --coverage 运行失败，防止覆盖率回退。
        // 实测口径（2026-10-10，npx vitest run --coverage 全量 18 文件/66 测试全绿）：
        //   All files lines = 20.87%（420/2012）⇒ floor 取 20（向下取整，见 W198 报告）。
        // 2026-10-10 实现轮复测（Node ≥20.19，jsdom 套件可全跑）：49 文件/199 测试全绿，
        //   All files lines = 31.22% / statements = 29.19%。floor 维持 20 不升：
        //   本机 PATH Node 20.18.1 下 jsdom 依赖链（html-encoding-sniffer→@exodus/bytes ESM）
        //   报 ERR_REQUIRE_ESM，仅 node-env 子集可跑 ⇒ 升门槛待 Node 基线统一 ≥20.19 后再执行。
        thresholds: {
          lines: 20,
        },
      },
    },
  };
});
