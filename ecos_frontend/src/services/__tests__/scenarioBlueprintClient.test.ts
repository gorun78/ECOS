// @vitest-environment node
/**
 * scenarioBlueprintClient 纯逻辑契约测试（node 环境）。
 *
 * <p>覆盖：路径单源构造（含 URL 编码）、状态词表与进度轨基准、tone/终态判定、
 * 409 错误码归类（非法迁移族 / 发布阻断族）。全部无 fetch、无 jsdom，
 * 断言对齐后端 ScenarioBlueprintController / BlueprintStatus / 异常族 errorCode。</p>
 */
import { describe, expect, it } from 'vitest';
import {
  API_BASE,
} from '../../services/httpClient';
import {
  blueprintCollectionUrl,
  blueprintItemUrl,
  blueprintActionUrl,
  BLUEPRINT_MAIN_FLOW,
  BLUEPRINT_EXCEPTION_STATES,
  parseBlueprintStatus,
  statusMainStep,
  isExceptionStatus,
  isTerminalStatus,
  statusTone,
  isIllegalTransitionCode,
  isPublishBlockedCode,
  type BlueprintStatus,
} from '../../services/scenarioBlueprintClient';

describe('scenarioBlueprintClient — 路径单源', () => {
  it('集合路径 = API_BASE + business/scenarios/{id}/blueprints，id 经 encodeURIComponent', () => {
    expect(blueprintCollectionUrl('sc:01')).toBe(`${API_BASE}/v1/business/scenarios/sc%3A01/blueprints`);
  });

  it('项路径在集合末尾追加 /{id}', () => {
    expect(blueprintItemUrl('sc1', 'bp/9')).toBe(`${API_BASE}/v1/business/scenarios/sc1/blueprints/bp%2F9`);
  });

  it('动作路径拼接子段（transition/validate/approve/release/revise/diff）', () => {
    expect(blueprintActionUrl('sc1', 'b1', '/transition')).toBe(
      `${API_BASE}/v1/business/scenarios/sc1/blueprints/b1/transition`,
    );
    expect(blueprintActionUrl('sc1', 'b1', '/enter-approval')).toMatch(/\/enter-approval$/);
  });
});

describe('scenarioBlueprintClient — 状态词表（§6.3 权威对齐）', () => {
  it('主链 11 态有序，首 DRAFT 终 COMPLETED', () => {
    expect(BLUEPRINT_MAIN_FLOW).toHaveLength(11);
    expect(BLUEPRINT_MAIN_FLOW[0]).toBe('DRAFT');
    expect(BLUEPRINT_MAIN_FLOW[BLUEPRINT_MAIN_FLOW.length - 1]).toBe('COMPLETED');
    expect(BLUEPRINT_MAIN_FLOW.indexOf('READY_FOR_APPROVAL')).toBeGreaterThan(BLUEPRINT_MAIN_FLOW.indexOf('VALIDATING'));
    expect(BLUEPRINT_MAIN_FLOW.indexOf('RELEASED')).toBe(BLUEPRINT_MAIN_FLOW.indexOf('APPROVED') + 1);
  });

  it('异常/回退态 5 个，与主链互斥', () => {
    expect(BLUEPRINT_EXCEPTION_STATES).toHaveLength(5);
    for (const s of BLUEPRINT_EXCEPTION_STATES) {
      expect(BLUEPRINT_MAIN_FLOW).not.toContain(s);
    }
  });

  it('parse：未知/空返回 null（禁默认值伪装），合法态原样返回', () => {
    expect(parseBlueprintStatus('ACTIVE')).toBe('ACTIVE');
    expect(parseBlueprintStatus('NOT_A_STATE')).toBeNull();
    expect(parseBlueprintStatus('')).toBeNull();
    expect(parseBlueprintStatus(undefined)).toBeNull();
    expect(parseBlueprintStatus(123)).toBeNull();
  });

  it('statusMainStep：主链给序，异常态给 -1', () => {
    expect(statusMainStep('DRAFT')).toBe(0);
    expect(statusMainStep('RUNNING')).toBe(9);
    expect(statusMainStep('BLOCKED')).toBe(-1);
    expect(statusMainStep(null)).toBe(-1);
  });

  it('isExceptionStatus / isTerminalStatus 判定正确', () => {
    expect(isExceptionStatus('VALIDATION_FAILED')).toBe(true);
    expect(isExceptionStatus('APPROVED')).toBe(false);
    expect(isTerminalStatus('COMPLETED')).toBe(true);
    expect(isTerminalStatus('DEPRECATED')).toBe(true);
    expect(isTerminalStatus('ACTIVE')).toBe(false);
  });

  it('statusTone 全态覆盖（供 UI 三重编码，无 unknown 漏网于已列态）', () => {
    const all: BlueprintStatus[] = [...BLUEPRINT_MAIN_FLOW, ...BLUEPRINT_EXCEPTION_STATES];
    for (const s of all) {
      expect(['initial', 'progress', 'ready', 'approved', 'live', 'terminal', 'exception']).toContain(statusTone(s));
    }
    expect(statusTone(null)).toBe('unknown');
  });
});

describe('scenarioBlueprintClient — 409 错误码归类', () => {
  it('非法迁移族（后端 IllegalBlueprintTransitionException 四码）', () => {
    for (const code of ['ILLEGAL_TRANSITION', 'TERMINAL_STATE', 'DRAFT_DIRECT_ACTIVE_FORBIDDEN', 'GATE_NOT_PASSED'] as const) {
      expect(isIllegalTransitionCode(code)).toBe(true);
    }
    expect(isIllegalTransitionCode('BLUEPRINT_VALIDATION_FAILED')).toBe(false);
  });

  it('发布阻断族（PublishBlockedException 三码）', () => {
    for (const code of ['BLUEPRINT_VALIDATION_FAILED', 'BLUEPRINT_APPROVAL_MISSING', 'BLUEPRINT_ALREADY_RELEASED'] as const) {
      expect(isPublishBlockedCode(code)).toBe(true);
    }
    expect(isPublishBlockedCode('ILLEGAL_TRANSITION')).toBe(false);
  });
});
