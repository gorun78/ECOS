// @vitest-environment node
/**
 * C1 基址单源末批（#143/B5-26）取证门 —— 散点 15 文件 21 处私设基址派生化，
 * `api-base-single-source` 基线由 15 枚归零（差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批与前九批的形状差在**站点分布**而非数量：15 文件跨 5 个目录深度（`hooks/`、`pages/` 根、
 * `pages/data-quality/api/`、`pages/knowledge/{pages,pages/components/govern,tabs}/`、`providers/`），
 * 故「导入腿相对路径随深度正确」在这里是真判据（写错一档 tsc 会报，但门禁本身检不出；深度表逐条实跑读出）。
 * 21 处形态拆分 = **8 单引号 + 2 双引号 + 11 反引号**（逐条取自 `git show HEAD:`，非手抄）；
 * 双引号两枚系 `pages/data-quality/api/_base.ts` 的 `DQ_GOV_BASE`/`DQ_LEGACY_BASE` 常量。</p>
 *
 * <p>两枚站点是前九批没有的形状，通用「行内派生态还原」吃不到，各自单列一条逐字腿：
 * ① `RuleRepoTable.tsx` 的 `compliance-rules?${…}` 模板**跨 5 行**，行级正则吃不进换行 ⇒ 改判
 * 「模板左半逐字 + 行内还原恰 4 条（差额即本腿）+ 整文件零裸面」；② `GraphBuilderTab.tsx` 不是传输
 * URL 而是 **JSX 显示文本**（改前 `waiting PMO-56 backend \`/api/v1/knowledge/sync/jobs\``），
 * 派生化会把面值带进模板插值，本批改成转义反引号形态，本腿按壳层剥离还原渲染字节并与改前逐字比。</p>
 *
 * <p>本批自伤一处、由本门形状判据抓出，记此防复发：转换脚本对 10 枚单/双引号站点漏剥 `/api` 前缀
 * ⇒ 产出 `` `${API_BASE}/api/v1/…` `` 双前缀（面值改变，「派生态还原 = HEAD 快照」一比即红）。
 * 关键判读：这种畸形在**源码串面**并不含 `/api/api`（前缀要等运行期拼接才现形），所以双前缀腿必须扫
 * 还原后的派生态面值而非源码行——本门据此把整 src 面按面值判，并在「能变红」腿里把「源码面 0 命中 /
 * 面值面 1 命中」并列表出来，作为 D-B5-40 盲区形状的第二例实证。</p>
 *
 * <p>闭合面（末条腿，实跑读出）：全仓 681（#142/B4-3e-76 删两枚页内 LucideIcon 桥后 679）个
 * 生产文件里代码行 `["'`]/api` 命中**只剩单源本体**
 * `services/httpClient.ts` 的 2 处（`export const API_BASE = "/api"` + 健康探针 `fetch("/api/health")`）
 * ⇒ C1 私设基址面清零。另记与 D-B5-40 的分工，防止把「基线归零」读成「C1 全闭合」：
 * 「同一 URL 族的第二生产方」仍由 `urlFamilyDualFormRatchet`（dupBoth 30 族 / 69 属主）记账，属 B6 存量债。</p>
 *
 * <p>#145/B6-4 追加口径（本门形状变一次，防误读成"批次回退"）：本批 21 处中的 `ExtractionPage.tsx`
 * 唯一一枚站点（`/api/v1/ontology/mappings` 族根）被后续存量债批上收到属主 `services/ontology/`
 * `ontologyMappingApi.ts` 导出的 `ONTOLOGY_MAPPINGS_BASE`，该页 `API_BASE` 导入合法归零、使用处变裸引用。
 * 本门**不剪腿**（19 条面值与逐文件条数一字不动，改由 `faceValues` 接住模板腿 + 裸引用两种形态），
 * 「导入腿恰 1 条」对这唯一一枚上收文件换成「API_BASE 恰 0 条 + 族单源导入腿恰 1 条且取自属主」，
 * 并把上收面钉成恰该一个文件。C1 闭合面腿的 `derTotal` 口径不变（只看全仓 `${API_BASE}` 派生态），
 * 上收会把该计数按面值减少 ⇒ 下限按复跑实测订正，订正理由记在棘轮门与本门注释，不得当成进度。</p>
 *
 * <p>#145/B6-5 再次同形订正下限 333→**331**（实测读出，与本批棘轮 derSites 312→310 差额同值）：
 * 本门 FILES 面**不含**该批两文件（`pages/data-workbench/api/_base.ts` 属 #143/B5-21 域门、且该域门
 * 只由「整域零私设基址」腿覆盖、不钉 `_base.ts` 条数；`pages/aiworkbench/api.ts` 由 B5-25 域门钉 28 条
 * 面值、上收后靠「导入裸名」判据接住，条数一字未动）。故本门只有全仓 `derTotal` 这一条腿需要跟着动，
 * 19 条面快照与逐文件条数一律不剪。下限每动一次都必须带实测号 + 缘由 + 与棘轮差额对账，缺一即视为漂移。</p>
 *
 * <p>#145/B6-6 订正下限 331→**326**（−5，实测读出，与本批棘轮 derSites 310→305 同值）。这是 B6 各批里
 * **首批"消费腿减少 + 属主声明净增"同时发生**的账：query/rag 两族改前没有任何常量，收口时新建 2 枚
 * `` `${API_BASE}…` `` 声明，同时 7 枚消费腿改成裸引用/上收 ⇒ 明细 −7/+2，不能按"消费腿条数"一口价写成 −7。
 * 本门 FILES 面仍不含该批三文件（各由 B5-18/B5-20/B6-6 域门覆盖），故依旧只动 `derTotal` 一条腿。</p>
 *
 * <p>#145/B6-7 是本批 15 文件里**第一次有 FILES 面成员被后续存量债批上收**（前两枚上收都落在别批的文件上，
 * B6-4 的 `ExtractionPage.tsx` 是本批成员但那批只有一枚站点）。`RuleRepoTable.tsx` 的 5 枚站点全部改引
 * 该族属主 `COMPLIANCE_RULES_BASE` ⇒ 本门三处形状随之变：① `FAMILY_OWNER` 从一枚变两枚（上收面按名字排序
 * 逐字钉死）；② `faceValues` 从"单常量硬编码"改成**常量表驱动**，否则这 4 枚行内腿凭空消失、19 条面快照
 * 掉到 15 条却看着像进度；③ 跨行腿的左半逐字从 `` `${API_BASE}` `` 形换成属主常量形，「行内还原恰 4 条」
 * 的判据口径同步从 `derivedValues` 换成 `faceValues`。</p>
 *
 * <p>**本批自伤一处、由条数腿抓出，记此防复发**：表驱动把「裸引用也算腿」这条 B6-4 引入的判据一并作用到了
 * 跨行模板的**开口行**上——模板腿正则要求同行收尾反引号吃不进，裸名 `split(name)` 却照样吃到，于是
 * RuleRepoTable 的行级面从 4 变 5（实测 `expected 5 to be 4`）。修法不是把条数改成像对，而是补
 * `isOpenTemplateLine`（奇数枚未转义反引号 ⇒ 该行不属于行级面，该站由跨行腿单独判），并把「开口行不造腿」
 * 与「同线两枚常量各计一次」两条 fixture 钉进「判据能变红」腿。旧形状没有这一缺陷：`API_BASE` 从不参与
 * 裸名计数。</p>
 *
 * <p>下限订正 326→**322**（−4，实测读出）。**首批与本批棘轮差额不同值**（棘轮 derSites 305→300 = −5）：
 * 差的 1 枚正是 RuleRepoTable 的跨行站——本门逐行跑 `DERIVED_RE` 看不到它，棘轮 `familiesOf` 滤掉注释行后
 * **拼回整串**再全局 `matchAll`，而 `[^\\]` 能吃换行 ⇒ 那一枚一直在棘轮面里。两口径本非同一面（棘轮只认
 * `/v1/` 族体，本门还计 `/api/telemetry`、`/api/graphql`），此前各批"两口径恰好同时看见同一批站点"才使
 * 差额同值，不得再按「差额必同值」外推。属主声明 `COMPLIANCE_RULES_BASE` 仍是 `${API_BASE}` 裸形态
 * （只是私有升导出）、wiki 侧 6 枚消费腿改前也不是 `${API_BASE}` 形态 ⇒ 两者都不计数，净 −4 全在该页。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { ONTOLOGY_MAPPINGS_BASE } from '../services/ontology/ontologyMappingApi';
import { COMPLIANCE_RULES_BASE } from '../pages/knowledge/services/wiki';
import { ECOS_VERSIONS_BASE } from '../services/ontology/ontologyVersionApi';
import { DQ_GOV_BASE } from '../pages/data-quality/api/_base';

// 本门位于 `src/__tests__/`，到 `src/` 的相对深度是一档
const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));
const CANONICAL = 'services/httpClient.ts';
/**
 * 全仓生产文件数（与本门同口径实跑读出，防扫描根漂移成零命中）。
 * #142/B4-3e-76 摘除两枚页内桥 `LucideIcon.tsx`（business/data-workbench，全局桥保留重写）
 * ⇒ 681→679（同批实扫实测读出：删两枚生产文件，−2，非手算；本门全量 run 实走面值与独立复走双证）。
 * #141/B3 认知治理批新增三枚生产文件（`services/cognitive/mindApi.ts` +
 * `pages/decision/DecisionMindList.tsx` + `pages/decision/DecisionMindDetail.tsx`，
 * 全量 run 实走面值 679→682，只增；新文件均走 `cognitiveClient` 单源出口、零私设基址，
 * residual/双前缀两段判据口径不变）。
 */
const PROD_FILES = 682;

/** 本批 15 文件：rel →（导入腿相对路径、本批派生点数）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string; sites: number }> = [
  { rel: 'hooks/useBindingCatalog.ts', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/ContractManager.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/DataAssetsDashboard.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/DataLineage.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/ProjectTracker.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/TenantManager.tsx', importPath: '../services/httpClient', sites: 2 },
  { rel: 'pages/TokenDashboard.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/data-quality/api/_base.ts', importPath: '../../../services/httpClient', sites: 2 },
  { rel: 'pages/knowledge/pages/ExtractionPage.tsx', importPath: '../../../services/httpClient', sites: 1 },
  // 5 点里 1 点是跨行模板（见 MULTILINE_REL），行级还原只吃到 4 点
  { rel: 'pages/knowledge/pages/components/govern/RuleRepoTable.tsx', importPath: '../../../../../services/httpClient', sites: 5 },
  { rel: 'pages/knowledge/tabs/DataWorkbenchImportTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'pages/knowledge/tabs/DocumentUploadTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  // JSX 显示文本位，行级还原不吃（见 JSX_REL）
  { rel: 'pages/knowledge/tabs/GraphBuilderTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'pages/knowledge/tabs/KnowledgeRuleRepositoryTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'providers/graphqlClient.ts', importPath: '../services/httpClient', sites: 1 },
];

const MULTILINE_REL = 'pages/knowledge/pages/components/govern/RuleRepoTable.tsx';
const JSX_REL = 'pages/knowledge/tabs/GraphBuilderTab.tsx';
/**
 * 被后续存量债批上收到**族单源常量**的本批文件（#145/B6-4 一枚 → #145/B6-7 两枚 → #145/B6-10 三枚）。这些页的
 * `API_BASE` 导入合法归零 ⇒ 本门「导入腿恰 1 条」对它们必须换成「恰 1 条族单源导入腿且取自属主」。
 * 面快照**不剪腿**（`faceValues` 接住模板腿 + 裸引用两种形态），剪腿会让 19 条掉到 18 条却看着像进度。
 *
 * <p>#145/B6-7 的 `RuleRepoTable.tsx` 是本批唯一含跨行模板站的页，其 5 枚站点（跨行 1 + 行内 4）
 * 全部改引 `COMPLIANCE_RULES_BASE` ⇒ 该页 `${API_BASE}` 派生态归零，本门的三处形状随之变：
 * ① `FAMILY_OWNER` 多一枚；② `faceValues` 从「单一常量」改为常量表驱动（否则 4 枚行内腿凭空消失）；
 * ③ 跨行腿的左半逐字从 `${API_BASE}` 形换成属主常量形，「行内还原恰 4 条」的判据口径同步从
 * `derivedValues` 换成 `faceValues`——差额仍是那 1 枚跨行站，一点不多一点不少。</p>
 */
const FAMILY_OWNER: ReadonlyMap<string, { name: string; ownerRe: RegExp }> = new Map([
  [
    'pages/knowledge/pages/ExtractionPage.tsx',
    { name: 'ONTOLOGY_MAPPINGS_BASE', ownerRe: /from '[^']*services\/ontology\/ontologyMappingApi'/ },
  ],
  [
    MULTILINE_REL,
    { name: 'COMPLIANCE_RULES_BASE', ownerRe: /from '[^']*services\/wiki'/ },
  ],
  // #145/B6-10：版本列表腿上收到 `services/ontology/ontologyVersionApi.ts` 的 `ECOS_VERSIONS_BASE`，
  // 页面经门面 `services/ontologyApi.ts` 取值 ⇒ ownerRe 认门面路径而非分册路径。
  [
    'pages/knowledge/tabs/DataWorkbenchImportTab.tsx',
    { name: 'ECOS_VERSIONS_BASE', ownerRe: /from '[^']*services\/ontologyApi'/ },
  ],
]);
/**
 * 通用行级还原可吃到的站点：跨行模板那 1 点从 RuleRepoTable 的 5 点里减掉，
 * JSX 显示文本站整个不吃 ⇒ 19 = 21 − 1（跨行）− 1（JSX）。
 */
const LINE_VISIBLE = FILES
  .filter((f) => f.rel !== JSX_REL)
  .map((f) => (f.rel === MULTILINE_REL ? { ...f, sites: f.sites - 1 } : f));

/** 跨行模板站点：改后左半 / 改前左半（快照里右半在后续行，脚本以 ` …<<MULTILINE>>` 标注截断）。
 *  #145/B6-7：改后左半的基址名从 `${API_BASE}` 换成该族属主常量，面值等价由下面的替换律证明。 */
const MULTILINE_HEAD = '`${COMPLIANCE_RULES_BASE}?${';
const MULTILINE_SNAP_HEAD = '`/api/v1/knowledge/compliance-rules?${';
/** JSX 显示文本站：改前整行（`git show HEAD:` 读出）与其中的渲染面值。 */
const JSX_PRE_HEAD =
  '<p className={`text-[10px] ${styles.muted} font-mono`}>waiting PMO-56 backend `/api/v1/knowledge/sync/jobs`</p>';
const JSX_SNAP = '`/api/v1/knowledge/sync/jobs`';

const HARDCODED_RE = /["'`]\/api\b/;
const DERIVED_RE = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
const NO_SLASH_AFTER_BASE = /`\$\{API_BASE\}(?!\/)/;
const DOUBLE_PREFIX_RE = /\/api\/api\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, ...rel.split('/')), 'utf8');
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function walk(dirRoot: string, out: string[]): string[] {
  for (const entry of readdirSync(join(SRC_ROOT, dirRoot))) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(SRC_ROOT, dirRoot, entry);
    if (statSync(full).isDirectory()) {
      walk(`${dirRoot}/${entry}`, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src)) {
    for (const m of line.matchAll(DERIVED_RE)) out.push(`${API_BASE}${m[1]}`);
  }
  return out;
}

/**
 * 族单源常量表（#145/B6-4 一枚 → #145/B6-7 两枚 → #145/B6-10 三枚 → #145/B6-14 四枚）：名字 + 运行期面值。
 * 判据按表驱动，后续批再上收一枚只需加一行——上一版的单常量硬编码正是本门第一次需要跟着改形状的原因。
 *
 * <p>`DQ_GOV_BASE` 是表里第一枚**别名层**成员：`_base.ts` 自 #145/B6-14 起不再书写 URL 体
 * （`export const DQ_GOV_BASE = DQ_GOVERNANCE_BASE;`），本门按裸名计数把该行计成一条面值腿——
 * 这是刻意口径：本门判的是「B5-26 那 21 处站点的运行期面值逐字不变」，别名重导出行确实原样携带该面值。
 * 「书写权是否已收归属主」不在本门面，由 `b6OrgTreeDqSingleSource` 的族根构造点判据钉死。</p>
 */
const FAM_SITES: ReadonlyArray<{ name: string; value: string; re: RegExp }> = [
  { name: 'ONTOLOGY_MAPPINGS_BASE', value: ONTOLOGY_MAPPINGS_BASE },
  { name: 'COMPLIANCE_RULES_BASE', value: COMPLIANCE_RULES_BASE },
  { name: 'ECOS_VERSIONS_BASE', value: ECOS_VERSIONS_BASE },
  { name: 'DQ_GOV_BASE', value: DQ_GOV_BASE },
].map((f) => ({
  ...f,
  // 与 DERIVED_RE 同款尾体：`(?:\\.|(?!`)[^\\])*` —— 反引号不做正文、反斜杠只吃转义对
  re: new RegExp('`' + String.raw`\$\{` + f.name + String.raw`\}` + '((?:\\\\.|(?!`)[^\\\\])*)' + '`', 'g'),
}));

/**
 * 未闭合反引号（奇数枚未转义反引号）⇒ 该行是跨行模板的**开口半边**。#145/B6-7 起必须显式跳过裸名计数：
 * 模板腿正则要求同行收尾反引号、吃不进 `` `${COMPLIANCE_RULES_BASE}?${ ``，但裸名 `split(name)`
 * 不看反引号，于是凭空造出第 5 枚腿（实测 `faceValues(RuleRepoTable)` = 5 而非 4）。
 * 这一站由跨行腿单独判，行级面不吃。旧形状无此缺陷——`API_BASE` 从不参与裸名计数。
 */
function isOpenTemplateLine(line: string): boolean {
  let n = 0;
  for (let i = 0; i < line.length; i += 1) {
    if (line[i] === '\\') { i += 1; continue; }
    if (line[i] === '`') n += 1;
  }
  return n % 2 === 1;
}

/**
 * 面值逐字不变腿的站点面 = 行内 `` `${API_BASE}…` `` 派生态 + 每枚族单源常量的两种形态
 * （模板腿与裸引用）。裸引用必须先摘模板腿、导入行不计、开口行不计，否则一条腿计两次、
 * 把 import 语句当成一次请求、或把跨行站造出第二条腿。
 * C1 闭合面腿不看这里（口径 = 全仓 `${API_BASE}` 派生态总量）。
 */
function faceValues(src: string): string[] {
  const out = derivedValues(src);
  for (const line of codeLines(src)) {
    if (/^\s*import\s/.test(line)) continue;
    const open = isOpenTemplateLine(line);
    let rest = line;
    for (const f of FAM_SITES) {
      for (const m of line.matchAll(f.re)) {
        rest = rest.split(m[0]).join('');
        out.push(`${f.value}${m[1]}`);
      }
      if (open) continue;
      const bare = rest.split(f.name).length - 1;
      for (let i = 0; i < bare; i += 1) out.push(f.value);
    }
  }
  return out;
}

/**
 * 改前裸形态快照（21 条 = 本腿 19 条 + 跨行 1 条 + JSX 1 条，带定界符原样收录，
 * `git show HEAD:` 实跑读出）：19 条走通用行级还原腿。
 */
const LINE_SNAP: readonly string[] = [
  "'/api/v1/workspace/binding-catalog'",
  '`/api/v1/ecos/objects/Contract?${params.toString()}`',
  "'/api/v1/datanet'",
  '`/api/v1/engine/data/lineage?tableName=${encodeURIComponent(tbl)}`',
  '`/api/v1/ecos/objects/Project?${params.toString()}`',
  '`/api/v1/system/tenants/${selectedTenantId}/usage?range=${usageRange}`',
  '`/api/v1/system/tenants/${selectedTenantId}/invoice?month=${invoiceMonth}`',
  '`/api/telemetry/tokens/summary?range=${range}`',
  '"/api/v1/dq"',
  '"/api/v1/ecos/dq"',
  "'/api/v1/ontology/mappings'",
  "'/api/v1/knowledge/compliance-rules'",
  '`/api/v1/knowledge/compliance-rules/${editingId}`',
  '`/api/v1/knowledge/compliance-rules/${r.id}`',
  '`/api/v1/knowledge/compliance-rules/${r.id}`',
  "'/api/v1/ecos/versions'",
  "'/api/v1/knowledge/extract/history'",
  "'/api/v1/knowledge/rules?groupBy=regulation'",
  "'/api/graphql'",
];

const UNWRAPPED = LINE_SNAP.map((s) => s.slice(1, -1));

describe('C1 基址单源末批散点 15 文件 — 私设基址派生化 (#143/B5-26)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('本批 15 文件代码行零私设基址，且基址来源唯一：httpClient 的 API_BASE 恰 1 条；已上收到族单源的文件改为恰 1 条族单源导入腿 (#143/B5-26 / #145/B6-4)', () => {
    for (const { rel, importPath } of FILES) {
      const lines = codeLines(read(rel));
      const hits = lines.filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
      const fam = FAMILY_OWNER.get(rel);
      const imports = lines.filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      if (fam === undefined) {
        expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
        expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
          new RegExp(`from '${importPath}'|from "${importPath}"`),
        );
      } else {
        expect(imports.length, `${rel} 已上收到族单源 ⇒ API_BASE 导入必须归零（非零即双来源）`).toBe(0);
        const famImports = lines.filter((l) => l.includes(fam.name) && l.trimStart().startsWith('import '));
        expect(famImports.length, `${rel} 应有且仅有 1 条 ${fam.name} 导入`).toBe(1);
        expect(famImports[0], `${rel} 的 ${fam.name} 导入未取自该族属主`).toMatch(fam.ownerRe);
      }
    }
    // 上收面必须钉死：多一个说明有文件被静默改成零来源以外的形状，少一个说明本批把某枚上收漏判成
    // 私设基址残留（假红/假绿双向）。#145/B6-7 起该面从 1 枚变 2 枚，按名字排序逐字钉。
    expect([...FAMILY_OWNER.keys()].sort()).toEqual([
      'pages/knowledge/pages/ExtractionPage.tsx',
      MULTILINE_REL,
      'pages/knowledge/tabs/DataWorkbenchImportTab.tsx',
    ].sort());
  });

  it('面值逐字不变：19 条行内站点（API_BASE 派生态 + 族单源常量两形态）还原后 = 改前裸形态快照（排序多重集 + 逐文件条数）', () => {
    const all: string[] = [];
    for (const { rel, sites } of LINE_VISIBLE) {
      const vals = faceValues(read(rel));
      expect(vals.length, `${rel} 行内派生态条数不符`).toBe(sites);
      all.push(...vals);
    }
    expect(all.length).toBe(19);
    expect([...all].sort()).toEqual([...UNWRAPPED].sort());
  });

  it('跨行模板腿：左半逐字（属主常量形）+ 行内还原恰 4 条（差额即本腿这条跨行站）+ 整文件零裸面', () => {
    const src = read(MULTILINE_REL);
    expect(codeLines(src).filter((l) => l.trim() === MULTILINE_HEAD).length).toBe(1);
    // 替换律：把左半的基址名换成该族属主常量的运行期面值，必须逐字等于改前快照左半
    // （#145/B6-7 之前这里换的是 `${API_BASE}` → API_BASE，形状同款、只是基址名换了）
    expect(MULTILINE_HEAD.replace('`${COMPLIANCE_RULES_BASE}', COMPLIANCE_RULES_BASE)).toBe(
      MULTILINE_SNAP_HEAD.slice(1),
    );
    expect(faceValues(src).length, '5 点里跨行那 1 点不该被行级还原吃到').toBe(4);
    expect(HARDCODED_RE.test(src), '跨行模板体里不得夹带 /api 字面量').toBe(false);
  });

  it('JSX 显示文本腿：按壳层剥离还原渲染面值 = 改前整行内文本逐字（转义反引号未改变渲染字节）', () => {
    const sites = codeLines(read(JSX_REL)).filter((l) => l.includes('waiting PMO-56 backend'));
    expect(sites.length).toBe(1);
    expect(JSX_PRE_HEAD).toContain(JSX_SNAP);
    const rendered = sites[0]
      .trim()
      .replace(/^.*\{`/, '')
      .replace(/`}.*$/, '')
      .replaceAll('\\`', '`')
      .replaceAll('${API_BASE}', API_BASE);
    expect(rendered).toBe(`waiting PMO-56 backend ${JSX_SNAP}`);
    expect(JSX_PRE_HEAD).toContain(rendered);
  });

  it('派生形态唯一：本批 15 文件内 `${API_BASE}` 后必接 /（既有 `${API_BASE}${rel}` 组合腿在别处，不在本面）', () => {
    const bad: string[] = [];
    for (const { rel } of FILES) {
      const src = read(rel);
      for (const line of codeLines(src)) if (NO_SLASH_AFTER_BASE.test(line)) bad.push(`${rel}: ${line.trim()}`);
      // 双前缀只在**还原后**现形（源码面是 `${API_BASE}/api/…`，字面 `/api/api` 并不存在于源码串），
      // 故本腿判派生态面值而非源码串——这与 D-B5-40 说的「引号判据对新形态瞎」是同一形状。
      for (const v of derivedValues(src)) if (DOUBLE_PREFIX_RE.test(v)) bad.push(`${rel}: 还原值 ${v}`);
    }
    expect(bad, `派生态畸形（\`\${API_BASE}\` 后不接斜杠 / 还原后双前缀）：\n${bad.join('\n')}`).toEqual([]);
  });

  it('C1 闭合面（整 src 实跑）：私设基址只剩单源本体 2 处，全仓派生态零 /api/api 双前缀', () => {
    const prod = walk('.', []);
    expect(prod.length, '生产文件数与本批实跑口径不符 ⇒ 扫描根漂移').toBe(PROD_FILES);
    const residual: string[] = [];
    const doubles: string[] = [];
    let derTotal = 0;
    for (const rel of prod) {
      const src = read(rel);
      for (const line of codeLines(src)) {
        if (HARDCODED_RE.test(line)) residual.push(`${rel} — ${line.trim()}`);
        for (const m of line.matchAll(DERIVED_RE)) {
          derTotal += 1;
          if (DOUBLE_PREFIX_RE.test(`${API_BASE}${m[1]}`)) doubles.push(`${rel} — ${line.trim()}`);
        }
      }
    }
    expect(residual.sort()).toEqual([
      `${CANONICAL} — const r = await fetch("/api/health");`,
      `${CANONICAL} — export const API_BASE = "/api";`,
    ]);
    // 全仓行内派生态下限锚在棘轮门 B5-25 实跑的 derSites 315 + 本批 21 −（跨行/JSX 两枚行级不吃）= 334。
    // #145/B6-4 订正为 333：该批把 5 枚**消费者**派生态上收为族单源 import 裸引用（属主声明一枚未动），
    // 本门只看 `${API_BASE}` 派生态 ⇒ 按面值减 5。这是可见性搬运、不是进度，下限随之同步下调并注明缘由；
    // 债的账仍只在 `urlFamilyDualFormRatchet` 的 dupBoth/属主面上记。
    // #145/B6-5 订正为 331：同形状再减 2 枚——`pages/data-workbench/api/_base.ts` 的 `PIPELINE_DEFS`
    // 声明改成不含 URL 体的别名重导出 + `pages/aiworkbench/api.ts` 的一枚内联派生态改 import 裸引用，
    // 属主声明（`api/dataClient`）一枚未动。与本批棘轮 derSites 312→310 的实测差额同值，互相对得上。
    // #145/B6-6 订正为 326：本批**首批出现净增声明**，明细 −7/+2 = −5 ——`services/knowledgeQueryApi.ts`
    // 3 枚消费者派生态改裸引用、`pages/knowledge/services/wiki.ts` 4 枚（glossary 族根声明 1 + rag 2 +
    // query 1）上收；同文件新建 2 枚属主声明（query/rag 两族改前**没有常量**，见
    // `b6KnowledgeQueryRagGlossarySingleSource` 的声明面 2→3 归因）。glossary 那枚族根声明只是改名导出、
    // `${API_BASE}` 形态未动 ⇒ 不计数。差额与本批棘轮 derSites 310→305 同值，互相对得上。
    // #145/B6-7 订正为 322（−4，实测读出）。**首批与本批棘轮差额不同值（棘轮 −5），差的那 1 枚是
    // RuleRepoTable 的跨行站**：本门逐行跑 DERIVED_RE，跨行模板的开口行没有收尾反引号 ⇒ 改前改后都不计；
    // 棘轮 `familiesOf` 先把注释行滤掉再**拼回整串**全局 matchAll，而 `[^\\]` 能吃换行 ⇒ 那一枚一直在它的
    // 面里。两口径本就不是同一面（C1 还含 `/api/telemetry`、`/api/graphql` 这类非 `/v1/` 前缀派生态，
    // 棘轮只认 `/v1/` 族体），此前各批恰好"两口径同时看见同一批站点"才使差额同值。
    // 属主声明 `COMPLIANCE_RULES_BASE` 仍是 `${API_BASE}` 裸形态（只是从私有升为导出）⇒ 计数不变；
    // wiki 侧 6 枚消费腿改引属主常量，改前也不是 `${API_BASE}` 形态 ⇒ 同样不计数。净 −4 全在 RuleRepoTable。
    // #145/B6-8 订正为 319（−3，实测读出）：删 `services/dataCatalogClient.ts` 的 `DATANET_BASE` 族根声明
    // 1 枚 + `services/datanetMetadataApi.ts` 两枚行内腿；该两文件改后的 5 枚腿一律写
    // `` `${DATANET_METADATA_BASE}/…` `` ⇒ 与本门 `${API_BASE}` 判据脱面。属主声明
    // `DATANET_METADATA_BASE` 只是 `api/dataClient.ts` 里 `DATANET_META` 改名并 export、同一声明位，
    // 计数不变（与 B6-7 的 `COMPLIANCE_RULES_BASE` 同形）。差额与本批棘轮 derSites 300→297 同值。
    // #145/B6-9 订正为 316（−3，实测读出）：删 4 枚消费点（`pages/MonitoringCenter.tsx` 的
    // ontology/cognitive 两枚 `ENGINES` 腿 + `pages/EngineMonitor.tsx` 的 `ENGINE_META.data.configPath`
    // + `pages/data-workbench/DataEngineConfigPanel.tsx` 的 PUT 保存腿），增 1 枚**新建**属主声明
    // （`api/dataClient.ts` 的 `DATA_ENGINE_SETTINGS_BASE`；前两批的 `COGNITIVE_ENGINE_BASE`/
    // `ONTOLOGY_ENGINE_BASE` 只是私有升导出、同一声明位不计）。差额与本批棘轮 derSites 297→294 **同值**，
    // 但这是「三族族体全在 `/v1/` 面内」才成立巧合，不得当成通则（B6-7 已实证两口径可以差 1 枚）。
    // #145/B6-10 订正为 312（−4，实测读出）：删 5 枚（OntologyDomainPanel 同文件两枚同值常量 +
    // BusinessWorkbenchLayout 的 `ENGINES` 取数腿 + 本批 DataWorkbenchImportTab 的版本列表腿 +
    // worldModelGraphApi 的私有 `WMG` 声明），增 1 枚**新建**属主声明（`ECOS_ONTOLOGIES_BASE`；
    // `ECOS_VERSIONS_BASE`/`ECOS_WORLD_MODEL_GRAPH_BASE` 只是私有升导出、同一声明位不计）。
    // 本批两口径差额又同值（棘轮 derSites 294→290），仍是巧合，不作通则。
    // #145/B6-11 订正为 304（−8，实测读出）：删 9 枚 `${API_BASE}` 行内站点——`GuardrailsView` 5 枚
    // 更新/删除/编译/预览腿（改前基址是 `./Guardrails/constants` 的**影子** `API_BASE`，本门按 token 名
    // 计数故照常计入；改后写 `${GUARDRAILS_POLICIES_BASE}/…` 与本门脱面）+ `cognitiveEngineApi` 的
    // 列表/新增 2 枚 + `pages/aiworkbench/api.ts` 的护栏列表腿 1 枚 + `agentMeshApi` 的私有 `MESH`
    // 族根声明 1 枚；增 1 枚**新建**属主声明（`GUARDRAILS_POLICIES_BASE`；`AGENT_MESH_BASE` 只是
    // 私有升导出、同一声明位不计）。本批两口径**不同值**（棘轮 pin 面值 290→287，−3），差 5 枚的成因实测拆清：
    // `GuardrailsView` 那 5 枚在棘轮里 strict／wide **两档都读 0**——棘轮 `familiesOf` 只认捕获体以 `/v1/`
    // 开头的族体，而该文件的族根本体已在导入常量里、拼接段是 `/${encodeURIComponent(id)}` 归一成 `/{}`
    // ⇒ 天然脱面（**与名字轴无关**，B6-11 首版此处曾误记成「别名隐形」，已按逐文件实测订正）；棘轮侧真正
    // 隐形的是 `Guardrails/constants.ts` 那枚影子族根（strict 恒 0、wide 显形 1），它使 pin 面值少减 1：
    // 以 wide 回读 HEAD 归档树是 291，wide 对 wide 才是 −4（删 5 增 1）。两口径差额本就不必相等，
    // B6-7 已实证（那里差 1 枚），此处差 5 枚且成因两类，逐档读数见 `urlFamilyDualFormRatchet` 文件头形状⑤。
    // #145/B6-12 订正为 302（−2，实测读出）：删 2 枚 `${API_BASE}` 行内站点——`ontologyWorkbenchApi`
    // 的 data 族根直写腿 1 枚 + `pages/knowledge/services/overview.ts` 的 export 族根直写腿 1 枚，
    // 两条都改成导入属主常量的**裸引用**（裸名不带 URL 体 ⇒ 对本门脱面）。两枚属主声明只是
    // 「私有改名 + 加 export」，仍在原声明位以 `` `${API_BASE}/v1/…` `` 形态书写 ⇒ 净增 0。
    // 本批两口径**不同值**：棘轮 pin 面值 287→285（−2）与本门同值，但属主枚 14→10（−4）——
    // −4 里的另 2 枚是两属主文件**自身**的族根书写点（改前各算一枚属主），它们在本门始终只计 1 枚
    // 声明行，故不重复出现在 −2 里；两口径的差是「同一声明位在两把尺子里的记法不同」，非漂移。
    // #145/B6-13（存量债第十三批）：302 → **301** = 删 1 枚行内派生态站点（`pages/SecurityAudit.tsx` 的
    // audit/stats 直写腿 1→0，改导入 `api/securityClient.ts` 导出的 `AUDIT_STATS_BASE` 裸引用）。属主文件那枚
    // 面值从「消费腿」搬到「族根声明」，两者都是 `` `${API_BASE}/v1/…` `` 行内形态 ⇒ 本门对它计数不变（20→20）。
    // api 域兄弟门 `apiDomainBaseSingleSource` 的多重集因此仍读 84：83 条改前快照 ⊎ 1 条 B6-9 台账，
    // 本批**不进** `OWNER_DECL_ADDITIONS`——该台账只登「面值原本不在本域任何腿上」的净新增声明位，
    // 而 `/api/v1/audit/stats` 改前就是 securityClient 的一条在册腿（登记会让期望值变 85、实测 84 即红）。
    // #145/B6-14（存量债第十四批）：301 → **299** = 删 2 枚行内派生态站点（`pages/data-quality/api/_base.ts`
    // 的 `DQ_GOV_BASE` 族根声明改成不含 URL 体的名别名 ＋ `pages/WorldModelViewer.tsx` 的整 URL 内联腿改裸名），
    // 与本批棘轮 derSites 284→282 同值（两族族体全在 `/v1/` 面内且无跨行站 ⇒ 两口径这次恰好同面，B6-7 已证不可当通则）；
    // 两枚属主声明（`api/dataClient.ts` 改名并 export ＋ `api/iamClient.ts` 内联提为声明）都是同值派生态 ⇒ 净新增 0。
    // #145/B6-15（存量债末批＝关账批）：299 → **297** = 删 4 枚行内派生态站点（beliefs 族 `pages/scenario/OverrideTab.tsx`
    // ＋ `ReplayTab.tsx` 各 1 枚列表腿、runs 族 `pages/data-workbench/api/pipeline.ts` ＋ `pipeline-editor/PipelineExecutionMonitor.tsx`
    // 各 1 枚轮询腿）、增 2 枚新建属主声明（`services/cognitiveClient.ts` 的 `COGNITIVE_BELIEFS_BASE` ＋
    // `api/dataClient.ts` 的 `ENGINE_DATA_PIPELINE_RUNS_BASE`，两枚都写 `` `${API_BASE}/v1/…` `` 行内形态、本门照计）
    // ⇒ 净 −2，与本批棘轮 derSites 282→280 同值（两族族体全在 `/v1/` 面内且无跨行站；B6-7 已证两口径同值不可当通则）。
    // 本批的账不在这里：dupBoth 2→0、BASELINE 属主 4→0 关账，详 `urlFamilyDualFormRatchet` 的 B6-15 段。
    // #142/B4-3e-47（差距 D-B4-76 前端腿）：297 → **296** = 删 1 枚行内派生态站点
    // （`pages/data-workbench/api/datasource.ts` 的 schema 预览腿，改前 1 枚 → 改后 0 枚，实测 `grep -c 'API_BASE}'`
    // 两侧 1/0；该腿族根上收为 `api/dataClient` 导出的 `DATANET_DATASOURCE_BASE` 裸引用 ⇒ 对本门脱面）。
    // 属主声明位 `` `${API_BASE}/v1/datanet/datasource` `` 只是「私有改名 + 加 export」，形态与位都没动 ⇒ 净增 0，
    // 全账净 −1 是幻影面退出的直接后果（幻影 URL 只有这一行在生产面上，prod 面 `ecos/data/datasource` 现零枚）。
    // 判据活性由本批两向实跑证得：改前 HEAD 实跑读 297 且旧锚通过，改后首跑即在 296 变红 ⇒ 不是把数字改成当前值。
    expect(derTotal, '行内派生态面须随批累积，低于 296 即扫描根或判据漂移').toBeGreaterThanOrEqual(296);
    expect(doubles, `全仓派生态 /api/api 双前缀：\n${doubles.join('\n')}`).toEqual([]);
  });

  /**
   * #142/B4-3e-47 新锚 296 的「能变红」承证。
   *
   * <p>棘轮锚一旦下调，光靠「改前 297 通过 / 改后 296 变红」只证明过一次。本腿把造成那 −1 的
   * 两行形状差直接喂给同一把尺子（`derivedValues`），断言旧形状吃到 1 枚、新形状脱面 ⇒
   * 若有人把预览腿改回 `` `${API_BASE}/v1/ecos/data/datasource/…` `` 内联形态，本门立即把该枚
   * 重新计回派生态面；反之若把属主常量声明写成不含 `API_BASE` 的形态，判据读数下降同样触锚。
   * 两向都由判据本身决定，不靠注释里的数字。</p>
   */
  it('判据能变红（#142/B4-3e-47）：预览腿旧幻影形状被计一枚、上收属主常量后的裸引用脱面', () => {
    const PRE_SHAPE = [
      "import { API_BASE } from '../../../services/httpClient';",
      'const raw = await post(`$' + '{API_BASE}/v1/ecos/data/datasource/preview-schema`, body);',
    ].join('\n');
    const POST_SHAPE = [
      "import { DATANET_DATASOURCE_BASE } from '../../../api/dataClient';",
      'const raw = await post(`$' + '{DATANET_DATASOURCE_BASE}/preview-schema`, body);',
    ].join('\n');
    expect(derivedValues(PRE_SHAPE)).toEqual([`${API_BASE}/v1/ecos/data/datasource/preview-schema`]);
    expect(derivedValues(POST_SHAPE)).toEqual([]);
    // 注释里出现旧形状不计（与 derTotal 扫描同一 `codeLines` 口径）：否则「写了个 TODO」也能把锚推上去
    expect(derivedValues(['  // 改前打的是 `' + '${API_BASE}/v1/ecos/data/datasource/preview-schema`;', 'fetch(x);'].join('\n'))).toEqual([]);
    // 生产面复核：改后全仓不得再有任何一行以 `API_BASE` 造 `/v1/ecos/data/datasource` 族（幻影族零生产成员）
    const revived: string[] = [];
    for (const rel of walk('.', [])) {
      for (const v of derivedValues(read(rel))) {
        if (v.startsWith(`${API_BASE}/v1/ecos/data/datasource`)) revived.push(`${rel}: ${v}`);
      }
    }
    expect(revived, `幻影族复活：\n${revived.join('\n')}`).toEqual([]);
  });

  it('快照自身可校验：21 条 = 8 单引号 + 2 双引号 + 11 反引号且全部以 /api 开头（防快照清空成恒真）', () => {
    const allRaw = [...LINE_SNAP, MULTILINE_SNAP_HEAD, JSX_SNAP];
    expect(allRaw.length).toBe(21);
    expect(allRaw.filter((s) => s.startsWith("'")).length).toBe(8);
    expect(allRaw.filter((s) => s.startsWith('"')).length).toBe(2);
    expect(allRaw.filter((s) => s.startsWith('`')).length).toBe(11);
    for (const v of allRaw.map((s) => s.replace(/^["'`]/, ''))) expect(v.startsWith('/api')).toBe(true);
    expect(FILES.reduce((acc, f) => acc + f.sites, 0)).toBe(21);
    expect(LINE_VISIBLE.reduce((acc, f) => acc + f.sites, 0)).toBe(19);
  });

  it('判据能变红：双前缀/不接斜杠/裸形态三类违例各被判出，合规源不命中；族单源三形态与跨行开口造腿亦各判出（防恒绿）', () => {
    const DEFECT = [
      '  const url = `${API_BASE}/api/v1/datanet`;', // 双前缀（本批真实自伤形状）
      '  const u2 = `${API_BASE}v1/ecos/versions`;', // 派生后不接斜杠
      "  const u3 = await apiFetchData('/api/v1/knowledge/rules');", // 裸形态残留
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../../services/httpClient';",
      '  const url = `${API_BASE}/v1/datanet`;',
      '  const u2 = await apiFetchData<RulesResponse>(`${API_BASE}/v1/knowledge/rules?groupBy=regulation`);',
      '  /** 注释里的 /api/v1/legacy 端点说明不计 */',
    ].join('\n');
    expect(derivedValues(DEFECT).filter((v) => DOUBLE_PREFIX_RE.test(v)).length).toBe(1);
    expect(codeLines(DEFECT).filter((l) => DOUBLE_PREFIX_RE.test(l)).length).toBe(0);
    expect(codeLines(DEFECT).filter((l) => NO_SLASH_AFTER_BASE.test(l)).length).toBe(1);
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(1);
    // 两类派生缺陷的还原面值各自畸形（双前缀 / 少斜杠粘连），一比即红
    expect(derivedValues(DEFECT)).toEqual(['/api/api/v1/datanet', '/apiv1/ecos/versions']);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(codeLines(CLEAN).filter((l) => NO_SLASH_AFTER_BASE.test(l))).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/datanet', '/api/v1/knowledge/rules?groupBy=regulation']);
    // #145/B6-4：族单源常量的裸引用与模板腿都必须还原成同面值，导入行不计
    expect(faceValues('  const data = await apiFetchData(ONTOLOGY_MAPPINGS_BASE);')).toEqual([
      '/api/v1/ontology/mappings',
    ]);
    expect(faceValues('  await apiFetchData(`${ONTOLOGY_MAPPINGS_BASE}/objects`);')).toEqual([
      '/api/v1/ontology/mappings/objects',
    ]);
    expect(faceValues("import { ONTOLOGY_MAPPINGS_BASE } from '../../../services/ontology/ontologyMappingApi';")).toEqual([]);
    // #145/B6-7：表驱动后第二枚族常量必须同样吃到三种形态，且**开口行不造腿**（本批真实自伤形状：
    // 加了裸名计数、忘加跨行守卫 ⇒ RuleRepoTable 行级面从 4 变 5，守恒腿一比即红）
    expect(faceValues('  await apiFetchData<void>(`${COMPLIANCE_RULES_BASE}/${r.id}`, { method: "DELETE" });')).toEqual([
      '/api/v1/knowledge/compliance-rules/${r.id}',
    ]);
    expect(faceValues('        `${COMPLIANCE_RULES_BASE}`')).toEqual(['/api/v1/knowledge/compliance-rules']);
    expect(faceValues('  const data = await apiFetchData(COMPLIANCE_RULES_BASE);')).toEqual([
      '/api/v1/knowledge/compliance-rules',
    ]);
    expect(faceValues('        `${COMPLIANCE_RULES_BASE}?${')).toEqual([]);
    expect(isOpenTemplateLine('        `${COMPLIANCE_RULES_BASE}?${')).toBe(true);
    expect(isOpenTemplateLine('        `${COMPLIANCE_RULES_BASE}`')).toBe(false);
    // 两枚常量的腿在同一条线上各计一次，互不吞并（防表驱动退化成"只认第一枚"）
    expect(
      faceValues('  const u = [ONTOLOGY_MAPPINGS_BASE, `${COMPLIANCE_RULES_BASE}/objects`];'),
    ).toEqual(['/api/v1/ontology/mappings', '/api/v1/knowledge/compliance-rules/objects']);
  });
});
