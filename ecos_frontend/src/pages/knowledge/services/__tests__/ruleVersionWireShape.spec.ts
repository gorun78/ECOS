// @vitest-environment node
/**
 * #147X 规则版本历史 wire 键单源（前端 `RuleVersion` ↔ 后端 `RuleVersionVO`）。
 *
 * <p>**本域缺陷本体**（改前）：`typesAndConstants.ts` 的 `RuleVersion` 写成 camelCase
 * （`ruleId/version/changeLog/content/createdBy/createdAt`），而后端 kb-engine
 * `RuleVersionVO` 用 `@JsonProperty` 逐键钉死 snake_case（`rule_id/version_number/
 * change_note/snapshot/changed_by/changed_at`＋#147V 新增 `git_ref/content_source/
 * content_reason`）。两侧键集除 `id` 外**零交集** ⇒ 任何按本类型取值的消费方拿到
 * `undefined` 却不报错（TS 只按声明校验，运行时不做键校验），呈现为「版本历史空态」的
 * 静默假象。live 取证见 `docs/30-设计/ECOS补充详细设计说明书-R7至R11-2026-10-16.md`
 * §2.9.9 ④（2026-11-02 #147V 回写：`GET /{id}/versions` 实际返回键集）。</p>
 *
 * <p>**判据面**：① 接口声明键集与后端 wire 键集**集合相等**（多键/少键都判红）；
 * ② 四个历史 camel 键名禁回流；③ 编译期夹具（`liveRow: RuleVersion`）逐键赋值，
 * 类型漂移时 `tsc --noEmit` 直接失败＝第二道闸；④ 服务腿运行时透传不改键名；
 * ⑤ 谓词自身有效性由「把改前键集喂给它必须判红」的反证夹具证明（防空判据）。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { RuleVersion } from '../../typesAndConstants';
import { RULE_CONTENT_SOURCE_VALUES } from '../../typesAndConstants';
import { fetchRuleVersions } from '../wiki';

const SRC_TYPES = readFileSync(
  fileURLToPath(new URL('../../typesAndConstants.ts', import.meta.url)),
  'utf8',
);

/** 后端单源：`RuleVersionVO` 的 `@JsonProperty` 键集（含 #147V 只增的三键）。 */
const WIRE_KEYS = [
  'id',
  'rule_id',
  'version_number',
  'snapshot',
  'changed_by',
  'changed_at',
  'change_note',
  'git_ref',
  'content_source',
  'content_reason',
] as const;

/** 改前的 camelCase 键名——回流即契约再次分叉。 */
const LEGACY_CAMEL_KEYS = ['ruleId', 'changeLog', 'createdBy', 'createdAt'] as const;

/** 取 `export interface X { ... }` 块体（含花括号，注释行不参与属性抽取）。 */
function interfaceBody(src: string, name: string): string {
  const start = src.indexOf(`export interface ${name} {`);
  if (start < 0) {
    throw new Error(`interface ${name} 未声明`);
  }
  const end = src.indexOf('\n}', start);
  if (end < 0) {
    throw new Error(`interface ${name} 块体未闭合`);
  }
  return src.slice(start, end + 2);
}

/** 声明的顶层属性名集合（两空格缩进 + `name:` 或 `name?:`）。 */
function declaredKeys(body: string): string[] {
  return body
    .split('\n')
    .filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line))
    .map((line) => line.match(/^\s{2}([A-Za-z_][A-Za-z0-9_]*)\??:/)?.[1])
    .filter((k): k is string => k !== undefined);
}

function sameKeySet(actual: readonly string[], expected: readonly string[]): boolean {
  const a = [...actual].sort();
  const b = [...expected].sort();
  return a.length === b.length && a.every((k, i) => k === b[i]);
}

describe('#147X 规则版本 wire 键单源', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('RuleVersion 声明键集与后端 wire 键集集合相等', () => {
    const keys = declaredKeys(interfaceBody(SRC_TYPES, 'RuleVersion'));
    expect(sameKeySet(keys, WIRE_KEYS)).toBe(true);
    // 逐枚点名，避免「集合相等」把缺键折叠成一条笼统失败
    WIRE_KEYS.forEach((k) => expect(keys).toContain(k));
  });

  it('改前 camel 键名零回流', () => {
    const body = interfaceBody(SRC_TYPES, 'RuleVersion');
    LEGACY_CAMEL_KEYS.forEach((k) => {
      expect(new RegExp(`\\b${k}\\??:`).test(body)).toBe(false);
    });
    // `version:` 亦属旧拼写（新侧为 version_number）
    expect(/(^|\n)\s{2}version\??:/.test(body)).toBe(false);
  });

  it('live 取证行可整体赋给 RuleVersion（编译期夹具：类型漂移即 tsc 红）', () => {
    const liveRow: RuleVersion = {
      id: '4f0b090a-3ec5-40e2-ba70-76dbdde16d9f',
      rule_id: '55da32ac-0abe-4604-a48f-383a559514cb',
      version_number: 1,
      snapshot: '{"id":"55da32ac","name":"PROBE-147V-git-read"}',
      changed_by: 'admin',
      // changed_at 为 BIGINT epoch 毫秒；写成 string 即 tsc 红
      changed_at: 1793595279963,
      change_note: 'Updated to version 2',
      git_ref: '1efb13d4fcfb8f4cd987fdb451aa856a15bca784',
      content_source: 'GIT',
      content_reason: undefined,
    };
    expect(Object.keys(liveRow).every((k) => (WIRE_KEYS as readonly string[]).includes(k))).toBe(true);
    expect(liveRow.changed_at).toBeTypeOf('number');
    expect(RULE_CONTENT_SOURCE_VALUES).toContain(liveRow.content_source);
  });

  it('内容来源三态与后端 RuleContentSource 同名同序', () => {
    expect([...RULE_CONTENT_SOURCE_VALUES]).toEqual(['GIT', 'DB_IN_USE', 'DB_FALLBACK']);
  });

  it('服务腿透传后端键名，不做 camel 化', async () => {
    const rows = [
      {
        id: 'v1',
        rule_id: 'r1',
        version_number: 1,
        snapshot: '{"a":1}',
        changed_by: 'admin',
        changed_at: 1793595279963,
        change_note: 'Updated to version 2',
        git_ref: '1efb13d4',
        content_source: 'GIT',
      },
    ];
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        ({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          text: () => Promise.resolve(JSON.stringify({ code: 0, success: true, data: rows })),
          json: () => Promise.resolve({ code: 0, success: true, data: rows }),
        }) as unknown as Response,
      ),
    );
    const got = await fetchRuleVersions('r1');
    expect(got).toHaveLength(1);
    expect(Object.keys(got[0]).sort()).toEqual(Object.keys(rows[0]).sort());
    expect(got[0].git_ref).toBe('1efb13d4');
    expect(got[0].content_source).toBe('GIT');
  });

  it('非数组响应折叠为空数组（现状行为，在册不放宽）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        ({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          text: () => Promise.resolve(JSON.stringify({ code: 0, success: true, data: null })),
          json: () => Promise.resolve({ code: 0, success: true, data: null }),
        }) as unknown as Response,
      ),
    );
    expect(await fetchRuleVersions('r1')).toEqual([]);
  });

  it('反证夹具：改前键集喂给同一谓词必须判红（防空判据）', () => {
    const legacySrc = `export interface RuleVersion {
  id: string;
  ruleId: string;
  version: number;
  changeLog: string;
  content?: string;
  createdBy?: string;
  createdAt: string;
}`;
    const legacyKeys = declaredKeys(interfaceBody(legacySrc, 'RuleVersion'));
    expect(sameKeySet(legacyKeys, WIRE_KEYS)).toBe(false);
    expect(/(^|\n)\s{2}version\??:/.test(interfaceBody(legacySrc, 'RuleVersion'))).toBe(true);
  });
});
