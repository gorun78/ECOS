/**
 * monitorBasePath.test.ts — D68：监控基址必须与后端 Controller 类级映射逐段一致
 *
 * 缺陷：`MONITOR_BASE` 曾写成 `${API_BASE}/v1/monitor`，而 `MonitorController` 类级
 * 映射是 `/api/monitor`（无 v1 段）⇒ `runSystemDiagnostics()` 恒 404「资源不存在」，
 * 「平台自检」Tab 显示「自检数据加载失败」。
 *
 * 注意：`/api/v1/monitor/**` 另有 `RuntimeAlertV1Controller`（alerts/dlq/ack），
 * 与本常量是**两个不同端点族**，本测试只钉住 monitorClient 这一支。
 */
import { describe, it, expect } from 'vitest';
import { MONITOR_BASE } from '../api/monitorClient';

describe('D68 monitorClient 基址', () => {
  it('MONITOR_BASE 不含 v1 段（MonitorController 类级映射为 /api/monitor）', () => {
    expect(MONITOR_BASE).toBe('/api/monitor');
  });

  it('派生 health 路径与服务端实存端点一致', () => {
    expect(`${MONITOR_BASE}/health`).toBe('/api/monitor/health');
  });
});
