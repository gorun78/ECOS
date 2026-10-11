// @vitest-environment node
/**
 * capabilityClient 纯逻辑契约测试（node 环境）。
 *
 * <p>覆盖：路径单源构造（含 URL 编码、无 "/api 字面量）、目录三要件缺失剔除、
 * costUnit/toolset 未知词返回 null（禁默认值伪装）、automationTone 分档、
 * 蓝图能力交叉引用抽取。全部无 fetch、无 jsdom，断言对齐后端
 * AiCapabilityController / CapabilityReadVO（2026-10-11 经 gateway 契约核验）。</p>
 */
import { describe, expect, it } from 'vitest';
import { API_BASE } from '../../services/httpClient';
import {
  capabilityCollectionUrl,
  capabilityActionUrl,
  capabilityTaskStatusUrl,
  parseCostUnit,
  parseToolset,
  automationTone,
  evidenceTone,
  parseCapabilityRow,
  extractDeclaredCapabilityIds,
  isRequiredByBlueprint,
  isCapabilityApiError,
  CapabilityApiError,
  type CapabilityRow,
} from '../../services/capabilityClient';

describe('capabilityClient — 路径单源（§13 /api/v1/ai/capabilities）', () => {
  it('集合路径 = API_BASE + /v1/ai/capabilities', () => {
    expect(capabilityCollectionUrl()).toBe(`${API_BASE}/v1/ai/capabilities`);
  });

  it('动作路径含 {id} 且经 encodeURIComponent', () => {
    expect(capabilityActionUrl('LLM TEXT', '/execute')).toBe(
      `${API_BASE}/v1/ai/capabilities/LLM%20TEXT/execute`,
    );
  });

  it('任务状态路径 = 集合/tasks/{taskId}/status', () => {
    expect(capabilityTaskStatusUrl('t/1')).toBe(
      `${API_BASE}/v1/ai/capabilities/tasks/t%2F1/status`,
    );
  });
});

describe('capabilityClient — 词表容错解析（禁默认值伪装）', () => {
  it('parseCostUnit 命中冻结词，未知返回 null', () => {
    expect(parseCostUnit('TOKEN')).toBe('TOKEN');
    expect(parseCostUnit(' call ')).toBe('CALL');
    expect(parseCostUnit('INPUT_SCALE')).toBe('INPUT_SCALE');
    expect(parseCostUnit('BYTE')).toBeNull();
    expect(parseCostUnit(undefined)).toBeNull();
    expect(parseCostUnit(42)).toBeNull();
  });

  it('parseToolset 命中 core/analytics/action，未知返回 null', () => {
    expect(parseToolset('core')).toBe('core');
    expect(parseToolset('ANALYTICS')).toBe('analytics');
    expect(parseToolset('action')).toBe('action');
    expect(parseToolset('mystery')).toBeNull();
  });

  it('automationTone 按档位分色，负值/NaN → unknown', () => {
    expect(automationTone(0)).toBe('none');
    expect(automationTone(1)).toBe('assist');
    expect(automationTone(2)).toBe('auto');
    expect(automationTone(-1)).toBe('unknown');
    expect(automationTone(Number.NaN)).toBe('unknown');
  });

  it('evidenceTone 布尔双档', () => {
    expect(evidenceTone(true)).toBe('required');
    expect(evidenceTone(false)).toBe('optional');
  });
});

describe('capabilityClient — 目录行三要件剔除', () => {
  it('合法行原样透传（含 evidenceRequired/automationLevel 归型）', () => {
    const row: CapabilityRow | null = parseCapabilityRow({
      capabilityId: 'TEXT_EMBEDDING',
      family: 'embedding',
      contractVersion: 'v1',
      evidenceRequired: true,
      automationLevel: 1,
      costUnit: 'TOKEN',
      toolset: 'core',
    });
    expect(row).not.toBeNull();
    expect(row!.capabilityId).toBe('TEXT_EMBEDDING');
    expect(row!.evidenceRequired).toBe(true);
    expect(row!.automationLevel).toBe(1);
  });

  it('缺 capabilityId/family/contractVersion 任一 → null（禁半装合法项）', () => {
    expect(parseCapabilityRow({ family: 'x', contractVersion: 'v1' })).toBeNull();
    expect(parseCapabilityRow({ capabilityId: 'X', contractVersion: 'v1' })).toBeNull();
    expect(parseCapabilityRow({ capabilityId: 'X', family: 'x' })).toBeNull();
    expect(parseCapabilityRow(null)).toBeNull();
    expect(parseCapabilityRow('str')).toBeNull();
  });

  it('automationLevel 非数值 → NaN（禁默认 0 伪装成「无自动化」）', () => {
    const row = parseCapabilityRow({
      capabilityId: 'X',
      family: 'f',
      contractVersion: 'v1',
      automationLevel: 'oops',
    });
    expect(row!.automationLevel).toBe(Number.NaN);
    expect(automationTone(row!.automationLevel)).toBe('unknown');
  });
});

describe('capabilityClient — 蓝图能力交叉引用（不新增后端真值）', () => {
  it('抽取字符串项与对象项 capabilityId/id', () => {
    const s = extractDeclaredCapabilityIds(['TEXT_EMBEDDING', { capabilityId: 'DOCUMENT_OCR' }, { id: 'LLM_TEXT_REASONING' }]);
    expect(s.has('TEXT_EMBEDDING')).toBe(true);
    expect(s.has('DOCUMENT_OCR')).toBe(true);
    expect(s.has('LLM_TEXT_REASONING')).toBe(true);
  });

  it('非数组/空 → 空集合（禁默认项）', () => {
    expect(extractDeclaredCapabilityIds(undefined).size).toBe(0);
    expect(extractDeclaredCapabilityIds({}).size).toBe(0);
  });

  it('isRequiredByBlueprint 集合命中', () => {
    const row = { capabilityId: 'TEXT_EMBEDDING' } as CapabilityRow;
    expect(isRequiredByBlueprint(row, new Set(['TEXT_EMBEDDING']))).toBe(true);
    expect(isRequiredByBlueprint(row, new Set([]))).toBe(false);
  });
});

describe('capabilityClient — 错误归一', () => {
  it('CapabilityApiError 实例守卫', () => {
    const e = new CapabilityApiError({ code: 'NOT_FOUND', status: 404, message: 'x' });
    expect(isCapabilityApiError(e)).toBe(true);
    expect(isCapabilityApiError(new Error('y'))).toBe(false);
  });
});
