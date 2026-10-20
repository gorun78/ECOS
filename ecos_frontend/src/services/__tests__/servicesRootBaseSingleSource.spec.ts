// @vitest-environment node
/**
 * servicesRootBaseSingleSource.spec.ts — `src/services/` 根域基址单源回归门（#143/B5-18，差距台账 §13.2.4 C1 腿第三批）。
 *
 * <p>本批把 `src/services/` 根十个基线在册文件代码行的私设基址整体改由 `API_BASE` 派生。派生只有
 * 一种正确性可证：**取值逐字不变**。故核心断言不是「长得对」，而是把每个派生点按 `API_BASE` 的实际
 * 值还原成字符串，与改前快照（48 条，2026-10-19 自 HEAD 实跑读出，非手抄）做多重集比对——多一条、
 * 少一条、差一个斜杠都判红。</p>
 *
 * <p>**判据口径（本批实证）**：私设基址有三种定界符形态，HEAD 实扫拆分 = 双引号 16 枚 + 单引号 7 枚
 * + 反引号 25 枚。若按窄口径 `["']` 去扫只能看到 23 枚，剩下 25 枚（占本批过半）会带着「已收口」的
 * 结论留在树里——这正是 #143/B5-17 照登的漏扫形状的放大版。本门因此把形态拆分写成快照自校验断言，
 * 并在 DEFECT 样本里三形态各配一条，使「窄口径化」必然判红。</p>
 *
 * <p>`/api/api` 双前缀腿只对代码行判定：`taskCenter.ts` 头部注释里有一句「避免拼成 /api/api/…」的
 * 历史防错说明（HEAD 既存，非本批新增），整文判定会把一句注释判成违例；注释面另由 `HARDCODED_RE`
 * 的行首剔除口径统一处理，与本门族其余判据一致。</p>
 *
 * <p>**#145/B6-3 起的判据形状（常量化会让面值面隐形）**：本批把 `aiworkbenchApi.ts` 的 5 条 AIP 腿
 * 改由 `pages/aiworkbench/api.ts` 导出的 `AIP_AGENTS_BASE` / `AIP_AUDIT_LOGS_BASE` 承载（2 条裸引用 +
 * 2 条 `` `${AIP_AGENTS_BASE}/${id}` `` 模板腿 + 1 条裸引用）。只认 `` `${API_BASE}…` `` 的旧判据对这
 * 5 条全盲——单模自跑读出 43 对 48，正是要防的「收口读成删请求」。故本门把解析表扩成三枚常量，并把
 * 裸引用计入腿（该文件不声明这两枚常量，不存在声明/使用双计）。**import 语句必须先整条剥**（含多行
 * 花括号体）：续行 `  AIP_AGENTS_BASE,` 若留下会被裸名判据吃成假腿。裸引用面只认这两枚 AIP 常量，
 * 不认 `API_BASE`——后者裸引用不构成 URL，纳入会把 import 行读成腿。</p>
 *
 * <p>**#145/B6-8 起的第二处基址来源**：本批把 `dataCatalogClient.ts` 的族根声明 `DATANET_BASE` 收口为
 * `api/dataClient.ts` 导出的 `DATANET_METADATA_BASE` 单源（属主落 api 层而非服务侧：该文件本就值导入
 * `../api` 门面，反向引用会成 `api.ts → dataClient → dataCatalogClient → api.ts` 循环）。该文件因此
 * **不再导入 `API_BASE`**，三条消费腿变成 `` `${DATANET_METADATA_BASE}/…` `` 形态——面值一条不少（快照里那枚
 * `'"/api/v1/datanet/metadata"'` 是改前族根声明，现由常量解析表还原成同面值），但「十文件一律
 * 从 ./httpClient 导入 API_BASE」这条腿的形状已不成立。本门把它改写成**每文件基址来源恰 1 条**：
 * 其余九文件仍是 `./httpClient` 的 `API_BASE`，`dataCatalogClient.ts` 是 `../api/dataClient` 的族单源；
 * 出现第二条来源（无论哪一种）即红。常量解析表同步增一枚，防止「单模自跑读出 47 对 48」的收口读成删请求。
 * **快照随之由 48 条订正为 50 条**：改前解析表不认 `DATANET_BASE`，该文件在册的只有族根声明那一枚面值
 * （三条消费腿对旧判据隐形，读出 1 条）；改后声明位消失、三枚腿经族单源常量还原成面值，读出 3 条，
 * 且这三枚运行期 URL 在改前同样会被请求出去——所以快照按运行期面值面登记为 3 条（形态由双引号 1 枚
 * 改记反引号 3 枚：dq 16→15、bt 25→28，dq+sq+bt 与 PER_FILE 之和同步为 50）。这不是债的增减，
 * 是同一面值守恒在更宽解析表下的正确读数。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../httpClient';
import { AIP_AGENTS_BASE, AIP_AUDIT_LOGS_BASE } from '../../pages/aiworkbench/api';
import { DATANET_METADATA_BASE } from '../../api/dataClient';

const SVC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const FILES = [
  'aiworkbenchApi.ts',
  'cognitiveClient.ts',
  'cognitiveEngineApi.ts',
  'dataCatalogClient.ts',
  'gitService.ts',
  'knowledgeNavApi.ts',
  'ontologyClient.ts',
  'ontologyWorkbenchApi.ts',
  'platformApi.ts',
  'taskCenter.ts',
] as const;

/** 私设基址判据：引号/单引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径，含反引号）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** 派生形态：`` `${常量}<path>` ``，捕获组即拼出来的路径段（#145/B6-3 起含 AIP 域单源常量）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;

/** 面值解析表：`API_BASE` 单源 + #145/B6-3 移到页面 api 层的两枚 AIP 基址常量 + #145/B6-8 的 datanet 元数据族单源。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['AIP_AGENTS_BASE', AIP_AGENTS_BASE],
  ['AIP_AUDIT_LOGS_BASE', AIP_AUDIT_LOGS_BASE],
  ['DATANET_METADATA_BASE', DATANET_METADATA_BASE],
]);

/**
 * 裸引用面只认这两枚 AIP 常量：本域十个文件都不声明它们（声明在页面 api 层），
 * 所以不存在「声明 + 使用」双计；`API_BASE` 不在此列——它裸引用不构成 URL。
 */
const BARE_REF_NAMES = ['AIP_AGENTS_BASE', 'AIP_AUDIT_LOGS_BASE'] as const;

/** import 语句整条先剥（含多行花括号体），否则续行 `  AIP_AGENTS_BASE,` 会被裸名判据吃成假腿。 */
const IMPORT_STMT_RE = /^\s*import\s[\s\S]*?;/gm;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split('\n').filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SVC_ROOT, rel), 'utf8');
}

/** 把派生点还原成运行期字符串面值（`${…}` 插值按字面保留，改前后同一写法）。 */
function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src.replace(IMPORT_STMT_RE, ''))) {
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const base = CONST_VALUES.get(m[1]);
      if (base !== undefined) out.push(base + m[2]);
    }
    for (const name of BARE_REF_NAMES) {
      const parts = rest.split(name);
      for (let i = 0; i < parts.length - 1; i++) {
        const before = parts[i].slice(-1);
        if (before === '{' || before === '.') continue;
        out.push(CONST_VALUES.get(name)!);
      }
    }
  }
  return out;
}

/**
 * 改前快照（HEAD 实跑读出，按「文件 × 行序」收录，带各自的定界符形态以便人工逐条核对与形态拆分自校验）。
 * 数组切片剥掉首尾定界符后即运行期面值。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  '"/api/v1/task"',
  '"/api/v1/task"',
  '"/api/v1/system/gateway-diagnostics"',
  '`/api/v1/monitor/alerts?${qs.toString()}`',
  '`/api/v1/monitor/alerts/${encodeURIComponent(id)}/ack`',
  '`/api/v1/monitor/dlq?${qs.toString()}`',
  '`/api/v1/monitor/dlq/${encodeURIComponent(id)}/replay`',
  '"/api/v1/ontology/data"',
  '"/api/v1/ontology/calibers"',
  '"/api/v1/ontology/functions/audit"',
  "'/api/v1/knowledge/nav'",
  '`/api/v1/knowledge/assets/status${qs}`',
  '`/api/v1/ecos/git/status${q(repoId)}`',
  '`/api/v1/ecos/git/commits${q(repoId, { path, limit })}`',
  '`/api/v1/ecos/git/commit${q(repoId)}`',
  '`/api/v1/ecos/git/branches${q(repoId)}`',
  '`/api/v1/ecos/git/diff${q(repoId, { commit1, commit2, path })}`',
  '`/api/v1/ecos/git/tag${q(repoId)}`',
  '`/api/v1/ecos/git/rollback${q(repoId)}`',
  '`/api/v1/ecos/git/branch${q(repoId)}`',
  '`/api/v1/ecos/git/push${q(repoId)}`',
  '`/api/v1/ecos/git/pull${q(repoId)}`',
  '`/api/v1/ecos/git/${action}${q(repoId)}`',
  '`/api/v1/datanet/metadata/resources/all`',
  '`/api/v1/datanet/metadata/fields/${resourceId}`',
  '`/api/v1/datanet/metadata/preview/${resourceId}?limit=${limit}`',
  '`/api/v1/world-model/state`',
  '`/api/v1/world-model/scenarios`',
  '`/api/v1/world-model/strategy/recommend`',
  '`/api/v1/world-model/causal-graph${q ? "?" + q : ""}`',
  "'/api/agent-mesh/agents'",
  "'/api/agent-mesh/missions'",
  "'/api/agent-mesh/route-intent'",
  "'/api/v1/guardrails/validate'",
  "'/api/v1/guardrails/policies'",
  "'/api/v1/guardrails/policies'",
  '`/api/v1/guardrails/policies/${id}`',
  '"/api/v1/cognitive"',
  '"/api/v1/business/scenarios"',
  '"/api/v1/engine/cognitive"',
  '"/api/v1/world-model"',
  '"/api/v1/aip/pipelines"',
  '`/api/v1/aip/pipelines/${id}`',
  '`/api/v1/aip/pipelines/${id}`',
  '"/api/v1/aip/agents"',
  '"/api/v1/aip/agents"',
  '`/api/v1/aip/agents/${id}`',
  '`/api/v1/aip/agents/${id}`',
  '"/api/v1/aip/guardrails"',
  '"/api/v1/aip/audit-logs"',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

/** 快照规模与三形态拆分（HEAD 实扫读出；#145/B6-8 起本域快照由 48 条订正为 50 条，见文件头）。 */
const TOTAL = 50;
const FORM_COUNTS = { dq: 15, sq: 7, bt: 28 } as const;
/** 每文件处数（HEAD 实扫读出，供「漏文件」自检）。 */
const PER_FILE = {
  'aiworkbenchApi.ts': 9,
  'cognitiveClient.ts': 4,
  'cognitiveEngineApi.ts': 11,
  'dataCatalogClient.ts': 3,
  'gitService.ts': 11,
  'knowledgeNavApi.ts': 2,
  'ontologyClient.ts': 2,
  'ontologyWorkbenchApi.ts': 1,
  'platformApi.ts': 5,
  'taskCenter.ts': 2,
} as const;

describe('services 根域基址单源 — 私设基址清零 (#143/B5-18)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('十文件代码行零私设基址；注释里的端点说明不计（与判据同口径）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行私设基址`).toEqual([]);
    }
  });

  it('十文件基址来源各恰 1 条（第二处来源即红，#145/B6-8 起含族单源形态）', () => {
    for (const rel of FILES) {
      const lines = codeLines(read(rel));
      const apiImports = lines.filter((l) => /^\s*import\s/.test(l) && /\bAPI_BASE\b/.test(l));
      const famImports = lines.filter((l) => /^\s*import\s/.test(l) && /\bDATANET_METADATA_BASE\b/.test(l));
      expect(apiImports.length + famImports.length, `${rel} 基址来源导入须恰 1 条`).toBe(1);
      if (rel === 'dataCatalogClient.ts') {
        // 本批收口：族根声明升为 api 层单源，服务侧只留族单源一跳
        expect(apiImports, `${rel} 已上收族单源，不得再导入 API_BASE`).toEqual([]);
        expect(famImports[0]).toMatch(/from ['"]\.\.\/api\/dataClient['"]/);
      } else {
        expect(famImports, `${rel} 不该出现族单源导入`).toEqual([]);
        expect(apiImports[0]).toMatch(/from ['"]\.\/httpClient['"]/);
      }
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），代码行零 /api/api 双前缀', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      const dbl = codeLines(src).filter((l) => /\/api\/api\b/.test(l));
      expect(dbl, `${rel} 代码行出现 /api/api 双前缀`).toEqual([]);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 快照（50 条，HEAD 实跑读出 + #145/B6-8 解析表订正）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(TOTAL);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('快照自身可校验：条数、三形态拆分与 /api/ 前缀（防快照被清空或窄口径化）', () => {
    expect(PRE_BATCH_URL_LITERALS.length).toBe(TOTAL);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
    const dq = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length;
    const sq = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length;
    const bt = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length;
    expect({ dq, sq, bt }).toEqual(FORM_COUNTS);
    expect(dq + sq + bt).toBe(TOTAL);
    const perFileTotal = Object.values(PER_FILE).reduce((a, b) => a + b, 0);
    expect(Object.keys(PER_FILE).sort()).toEqual([...FILES].sort());
    expect(perFileTotal, '每文件处数之和必须等于快照总数（防漏文件）').toBe(TOTAL);
  });

  it('判据能变红：三形态违例源各命中、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const SQ_BASE = '/api/v1/system/users';",
      'const DQ_BASE = "/api/v1/system/roles";',
      'return apiFetchData(`/api/v1/audit/stats`);',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, apiFetchData } from './httpClient';",
      'const BASE = `${API_BASE}/v1/system/users`;',
      'return apiFetchData(BASE);',
      '// 历史 "/api" 字面量只允许留在注释行（判据与门禁同口径）',
      '/** 注释里提到 /api/api/… 属防错说明，不计违例 */',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/system/users']);
    expect(codeLines(CLEAN).filter((l) => /\/api\/api\b/.test(l))).toEqual([]);
    // #145/B6-3：常量化后的三种形态必须仍被读成腿；多行 import 的续行必须不算腿
    const AIP = [
      'import {',
      '  AIP_AGENTS_BASE,',
      '  AIP_AUDIT_LOGS_BASE,',
      '} from "../pages/aiworkbench/api";',
      'return apiFetchData(AIP_AGENTS_BASE);',
      'return apiFetchData(`${AIP_AGENTS_BASE}/${id}`, { method: "PUT" });',
      'return apiFetchData(AIP_AUDIT_LOGS_BASE);',
    ].join('\n');
    expect(derivedValues(AIP)).toEqual([
      '/api/v1/aip/agents',
      '/api/v1/aip/agents/${id}',
      '/api/v1/aip/audit-logs',
    ]);
    // 把常量名当属性访问（`cfg.AIP_AGENTS_BASE`）不构成腿——防裸名判据吃进无关面
    expect(derivedValues('const n = cfg.AIP_AGENTS_BASE;')).toEqual([]);
  });
});
