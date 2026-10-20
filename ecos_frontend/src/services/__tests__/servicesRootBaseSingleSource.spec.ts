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
 *
 * <p>**#145/B6-11 起的第三处形状（本域自声明的族单源）**：本批把 `cognitiveEngineApi.ts` 的
 * list/create 两条 `` `${API_BASE}/v1/guardrails/policies` `` 内联腿收口为该文件自声明并导出的
 * `GUARDRAILS_POLICIES_BASE`（DELETE 腿 `` `${API_BASE}/v1/guardrails/policies/${id}` `` 按 B6-3 ③
 * 口径刻意保持行内以保住子族可见性）。这是本门第一次遇到**声明行与裸引用行同在本域文件内**的常量：
 * B6-3 那两枚 AIP 常量声明在页面层，本域只有使用行，所以「裸引用算腿」从来没有双计风险；本批若沿用
 * 「只算模板腿」会读出 49 对 50（把两条真实请求读成删请求），若「声明 + 两条裸引用」全算又读成 51
 * （把一枚声明位虚抬成请求）。故按 `b6*SingleSource` 各批门既有的形状 ⑧ 立**声明行整行跳过**规则
 * （`DECL_LINE_RE` 由 `BARE_REF_NAMES` 派生，不会与集合漂移），本域 50 条面值与 PER_FILE/FORM_COUNTS
 * **一律不动**。这条腿的可变性由「判据能变红」里新增的三段 fixture 承证（声明行不计、裸引用各计一枚、
 * 非集合成员的 const 声明行照常计腿——最后一段防把跳过规则扩大成"所有声明都不算腿"）。</p>
 *
 * <p>**#145/B6-12 起的第四枚裸引用常量（属主在本域之外的子目录）**：`ontologyWorkbenchApi.ts`
 * 的 `fetchOntologyDataJson` 由 `` `${API_BASE}/v1/ontology/data` `` 改取
 * `services/ontology/ontologyDataApi.ts` 导出的 `ONTOLOGY_DATA_BASE`。形状与 B6-3 的 AIP 两枚同类
 * （声明位不在本域十个文件里 ⇒ 无「声明 + 使用」双计风险），故快照 50 条、`FORM_COUNTS`、
 * `PER_FILE['ontologyWorkbenchApi.ts'] = 1` **一律不动**，只把解析表与裸引用集合各加一枚。
 * 「十文件基址来源恰 1 条」这条腿的形状随之改写：`FAM_BASE_SOURCE` 白名单里的文件（现两枚 =
 * `dataCatalogClient.ts` / `ontologyWorkbenchApi.ts`）必须是族单源导入且零 `API_BASE` 导入，
 * 其余八文件仍必须从 `./httpClient` 导入 `API_BASE`——两边都反向钉死，防止把族单源当成免检通道，
 * 也防止白名单被悄悄扩大。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../httpClient';
import { AIP_AGENTS_BASE, AIP_AUDIT_LOGS_BASE } from '../../pages/aiworkbench/api';
import { DATANET_METADATA_BASE } from '../../api/dataClient';
import { GUARDRAILS_POLICIES_BASE } from '../cognitiveEngineApi';
import { ONTOLOGY_DATA_BASE } from '../ontology/ontologyDataApi';
import { COGNITIVE_BELIEFS_BASE } from '../cognitiveClient';

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

/** 面值解析表：`API_BASE` 单源 + #145/B6-3 移到页面 api 层的两枚 AIP 基址常量 + #145/B6-8 的 datanet 元数据族单源 + #145/B6-11 的护栏策略族单源 + #145/B6-12 的本体数据族单源。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['AIP_AGENTS_BASE', AIP_AGENTS_BASE],
  ['AIP_AUDIT_LOGS_BASE', AIP_AUDIT_LOGS_BASE],
  ['DATANET_METADATA_BASE', DATANET_METADATA_BASE],
  ['GUARDRAILS_POLICIES_BASE', GUARDRAILS_POLICIES_BASE],
  ['ONTOLOGY_DATA_BASE', ONTOLOGY_DATA_BASE],
  ['COGNITIVE_BELIEFS_BASE', COGNITIVE_BELIEFS_BASE],
]);

/**
 * 裸引用面：`AIP_AGENTS_BASE` / `AIP_AUDIT_LOGS_BASE` 两枚在本域十个文件里**没有声明行**（声明在
 * 页面 api 层），所以不存在「声明 + 使用」双计；`API_BASE` 不在此列——它裸引用不构成 URL。
 *
 * **#145/B6-11 起本集合第一次出现"在本域自声明"的成员**：`GUARDRAILS_POLICIES_BASE` 就声明在
 * `cognitiveEngineApi.ts`（属主即该文件），若不处理就会撞上 B6-5 登记过的判据形状 ⑧
 * 「属主声明行自身不算腿」——声明行 `` export const X = `${API_BASE}/v1/guardrails/policies`; `` 里的
 * `${API_BASE}` 是一条**声明位**面值，同一文件另有两条裸引用（list / create）才是**请求腿**；
 * 三者全计会把 3 条腿读成 4 条（虚抬），三者全不计又把两条真实请求读成删请求（假红→误判为漏挪）。
 * 故这里采用与 `b6*SingleSource` 各批门一致的口径：**声明行整行跳过，裸引用行照常计腿**，
 * 本域快照维持 50 条面值不动（改前 list + create + delete 三条腿，改后仍是三条）。
 * 该规则由 `DECL_LINE_RE` 从 `BARE_REF_NAMES` 派生，两者不可能各走各路。
 *
 * **#145/B6-12 起的第四枚裸引用常量**：`ontologyWorkbenchApi.ts` 的 `fetchOntologyDataJson`
 * 腿改取 `services/ontology/ontologyDataApi.ts` 导出的 `ONTOLOGY_DATA_BASE`（属主在本域之外的
 * 子目录，形状同 B6-3 的 AIP 两枚 ⇒ 本域无声明行、不存在双计，`DECL_LINE_RE` 对本文件只是空转）。
 * 快照里那枚 `'"/api/v1/ontology/data"'` 由解析表还原成同面值，PER_FILE 仍记 1 条、TOTAL 仍 50。
 *
 * **#145/B6-15 起的第五枚裸引用常量＝本域第五批属主声明**：`COGNITIVE_BELIEFS_BASE` 声明在
 * `cognitiveClient.ts`（属主即该文件，同 B6-11 的 `GUARDRAILS_POLICIES_BASE` 形状），两条消费腿在
 * `pages/scenario/` 域外。若不登记，这条 `export const X = \`${API_BASE}/v1/cognitive/beliefs\`;`
 * 声明位会被 SITE_RE 的 `API_BASE` 腿吃成第 51 条面值（实跑读出 50→51 即此）。按 B6-5 ⑧
 * 「属主声明行不算腿」入 `BARE_REF_NAMES` ⇒ `DECL_LINE_RE` 整行跳过，本域快照 50 条不动、
 * `PER_FILE['cognitiveClient.ts']` 仍 4 条——本批本域一条请求腿也未增减，收口发生在域外两页面。
 */
const BARE_REF_NAMES = ['AIP_AGENTS_BASE', 'AIP_AUDIT_LOGS_BASE', 'GUARDRAILS_POLICIES_BASE', 'ONTOLOGY_DATA_BASE', 'COGNITIVE_BELIEFS_BASE'] as const;

/** 族单源导入名（#145/B6-8 起本域允许的第二处基址来源；一枚文件至多一种来源）。 */
const FAM_BASE_NAMES = ['DATANET_METADATA_BASE', 'ONTOLOGY_DATA_BASE'] as const;

/** 允许从族单源取基址的文件 → 期望的导入路径；其余文件必须仍从 `./httpClient` 导入 `API_BASE`。 */
const FAM_BASE_SOURCE: Readonly<Record<string, string>> = {
  'dataCatalogClient.ts': '../api/dataClient',
  'ontologyWorkbenchApi.ts': './ontology/ontologyDataApi',
};

/** 属主声明行（B6-5 ⑧）：只匹配「以本集合成员为声明目标」的 const/let/var 行，别的行一律照常数腿。 */
const DECL_LINE_RE = new RegExp('^\\s*(?:export\\s+)?(?:const|let|var)\\s+(?:' + BARE_REF_NAMES.join('|') + ')\\s*=');

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
    if (DECL_LINE_RE.test(line)) continue;
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
        // **#145/B6-11 补的名字边界判据**：`split(name)` 会把 `GUARDRAILS_POLICIES_BASEX` 这类前缀相似
        // 的更长标识符吃成一条腿（反向腿 2 的 fixture 即为此而设）。裸名判据必须两头都看：前一位已有
        // `{`/`.` 两条护栏，后一位只要还是标识符字符就不算这个常量的引用（`parts[i+1]` 首字符即后一位）。
        const after = parts[i + 1].charAt(0);
        if (/[A-Za-z0-9_$]/.test(after)) continue;
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
      const famImports = lines.filter(
        (l) => /^\s*import\s/.test(l) && FAM_BASE_NAMES.some((n) => new RegExp('\\b' + n + '\\b').test(l)),
      );
      expect(apiImports.length + famImports.length, `${rel} 基址来源导入须恰 1 条`).toBe(1);
      const expectedSource = FAM_BASE_SOURCE[rel];
      if (expectedSource) {
        // 本批收口：族根声明升为族单源，服务侧只留族单源一跳
        expect(apiImports, `${rel} 已上收族单源，不得再导入 API_BASE`).toEqual([]);
        expect(famImports.length, `${rel} 族单源导入不得多于 1 条`).toBe(1);
        // 该域源码用双引号书写 import 路径，先归一引号再比对（避免为两种引号各写一条正则）
        expect(famImports[0].replace(/"/g, "'")).toContain(`from '${expectedSource}'`);
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
    // #145/B6-11：本域**自声明**的族单源走 B6-5 形状 ⑧「声明行不算腿、裸引用算腿」。
    // 三条请求腿（list/create/delete）面值逐字可读；那一枚声明位不得虚抬成第四条。
    // 判据退化方向有两个，这里同时钉住：只算模板腿 ⇒ 读出 2 条（把两条真实请求读成删请求）；
    // 声明 + 裸引用全计 ⇒ 读出 4 条（把声明位读成请求）。正解是 3。
    const SELF_DECL = [
      "import { API_BASE, apiFetchData } from './httpClient';",
      'export const GUARDRAILS_POLICIES_BASE = `${API_BASE}/v1/guardrails/policies`;',
      'return apiFetchData(GUARDRAILS_POLICIES_BASE);',
      'return apiFetchData(GUARDRAILS_POLICIES_BASE, { method: "POST" });',
      'return apiFetchData(`${API_BASE}/v1/guardrails/policies/${id}`, { method: "DELETE" });',
    ].join('\n');
    expect(derivedValues(SELF_DECL)).toEqual([
      '/api/v1/guardrails/policies',
      '/api/v1/guardrails/policies',
      '/api/v1/guardrails/policies/${id}',
    ]);
    // 反向腿 1：跳过规则只认「声明目标是集合成员」的行；集合外的常量声明照常计腿
    //（防把 ⑧ 扩大成"所有声明行都不算腿"，那会让本域 50 条快照静默漏计）。
    expect(derivedValues('const OTHER_BASE = `${API_BASE}/v1/guardrails/other`;')).toEqual(['/api/v1/guardrails/other']);
    // 反向腿 2：名字前缀相似（`…BASEX`）不得被当成集合成员的声明行——正则组后紧跟 `\s*=` 即词边界判据。
    expect(derivedValues('const GUARDRAILS_POLICIES_BASEX = `${API_BASE}/v1/guardrails/other`;')).toEqual(['/api/v1/guardrails/other']);
    // 把常量名当属性访问（`cfg.AIP_AGENTS_BASE`）不构成腿——防裸名判据吃进无关面
    expect(derivedValues('const n = cfg.AIP_AGENTS_BASE;')).toEqual([]);
    // #145/B6-12：属主在**本域之外**（services/ontology/）的裸引用常量必须读成腿——
    // 漏读会把 `fetchOntologyDataJson` 这条真实请求读成删请求（50 变 49 却无人解释）。
    expect(
      derivedValues(
        [
          'import { ONTOLOGY_DATA_BASE } from "./ontology/ontologyDataApi";',
          'return doFetch(ONTOLOGY_DATA_BASE);',
        ].join('\n'),
      ),
    ).toEqual(['/api/v1/ontology/data']);
    // 反向：前缀相似的标识符（`…BASEX`）不得被当成该常量的腿
    expect(derivedValues('return doFetch(ONTOLOGY_DATA_BASEX);')).toEqual([]);
  });
});
