/**
 * scenarioAnalysisBase — workspace 分析/配置基址单源（场景工作台简化 v0.3）
 *
 * <p>与 {@code services/scenarioActionClient.ts} 同源：统一由本文件持有
 * {@code /api/v1/workspace} 前缀，消费方不再各自拼接（避免双生产方棘轮）。</p>
 */

import { API_BASE } from './httpClient';

export const WORKSPACE_ANALYSES_BASE = `${API_BASE}/v1/workspace`;
export const WORKSPACE_CONFIG_BASE = `${API_BASE}/v1/workspace`;
