// @vitest-environment node
/**
 * schemaPreviewContract.spec.ts — Schema 预览通道前后端键面契约门（#142/B4-3e-47，差距 D-B4-76 前端腿）。
 *
 * <p><b>为什么单独立这道门</b>：D-B4-76 的原始形态不是「报错了」，而是<b>两侧各说各话却都不报错</b>——
 * 前端打的是全仓零承流的幻影路径，后端即便有人接、返回键面也是 `objects`/`columnPreview`，
 * 前端读的是 `fields`/`tableNames`，于是恒读空。这类缺陷只靠本域行为测试测不出来：
 * 行为测试的夹具是前端同学按自己的想象写的，键面一漂就一起漂。故本门把<b>后端 Java 源码</b>
 * 当成事实来源，逐面比对四个契约面（承流路径／响应键面／列面键名／请求体键名），
 * 任何一侧改名而另一侧未跟，都在这里变红。</p>
 *
 * <p><b>与相邻登记的分界</b>：数据源<b>类型词表</b>（前端 `driverMap`／后端 `validateType` 白名单／
 * 底座连接器 bean 三面）分裂属 D-B4-78，尚未裁决 ⇒ 本门<b>不</b>对类型集合做任何等值断言，
 * 免得把未裁的裁决当成已裁写进门里。本门只管「键名与路径」两面。</p>
 *
 * <p><b>读后端源码的根据</b>：与 `src/services/__tests__/sseFrames.singleSource.spec.ts`
 * 同口径（该门早已跨读 `ecos_backend/`）。文件缺失即判红，禁 skip——跨语言门一旦静默跳过，
 * 就成了「两侧都改、门永远绿」的那个洞。</p>
 */
import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  buildSchemaPreviewDto,
  parseSchemaPreviewResponse,
} from '../datasource';
import { DATANET_DATASOURCE_BASE } from '../../../../api/dataClient';

const REPO_ROOT = fileURLToPath(new URL('../../../../../../', import.meta.url));
const BACKEND = {
  controller: `${REPO_ROOT}ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataSourceController.java`,
  service: `${REPO_ROOT}ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/DataSourceServiceImpl.java`,
  registry: `${REPO_ROOT}ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/DataSourceRegistryService.java`,
  dto: `${REPO_ROOT}ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/data/dto/DataSourceDTO.java`,
} as const;

/** 属主端点的子路径（类级前缀之后的部分）。 */
const OWNER_SUB_PATH = '/preview-schema';

/** 响应键面契约：3 必需 + 4 可选（可选键＝条件写，缺省即「该面未给」）。 */
const BACKEND_RESPONSE_KEYS = [
  'type', 'tableNames', 'fields',
  'fieldsObject', 'fieldsDegraded', 'fieldsReason', 'note',
] as const;

/** 列面（每枚字段）键名契约。 */
const BACKEND_COLUMN_KEYS = ['name', 'type', 'required', 'comment'] as const;

function readJava(which: keyof typeof BACKEND): string {
  const path = BACKEND[which];
  if (!existsSync(path)) throw new Error(`后端契约源缺失（跨语言门禁静默跳绿）：${path}`);
  return readFileSync(path, 'utf8');
}

/**
 * 切出某个 Java 方法的源码段（从签名标记起，到下一个同级方法/文档注释为止）。
 * 只用于把「本方法写的键」与「同文件其它方法写的键」分开——预览通道有两法
 * （按 ID 的 `previewSchema` 写 `objects`/`columnPreview`，按配置的 `previewSchemaByConfig`
 * 写 `tableNames`/`fields`），混在一起读就是假绿。
 */
function javaMethodSlice(src: string, signatureMarker: string): string {
  const start = src.indexOf(signatureMarker);
  expect(start, `后端源码里找不到方法标记：${signatureMarker}`).toBeGreaterThanOrEqual(0);
  const rest = src.slice(start + signatureMarker.length);
  const next = rest.search(/\n {4}(?:public|private|protected|@Override|\/\*\*)/);
  return next < 0 ? rest : rest.slice(0, next);
}

/** 取 `result.put("k", …)` 与 `field.put("k", …)` 的键名集合（去重、排序）。 */
function mapPutKeys(slice: string, receiver: string): string[] {
  const re = new RegExp(`\\b${receiver}\\.put\\(\\s*"([^"]+)"`, 'g');
  return [...new Set([...slice.matchAll(re)].map((m) => m[1]))].sort();
}

/** 取类级 `@RequestMapping` 里的路径数组（双形态：带 `/api/v1` 与不带）。 */
function classLevelPrefixes(src: string): string[] {
  const m = /@RequestMapping\(\s*\{([^}]*)\}\s*\)/.exec(src);
  expect(m, '承流 Controller 的类级 @RequestMapping 未能解析').not.toBeNull();
  return [...(m as RegExpExecArray)[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
}

describe('Schema 预览通道 — 前后端键面契约 (#142/B4-3e-47 / D-B4-76)', () => {
  it('前提：后端四个契约源文件都在位（禁把跨语言门静默跳成绿）', () => {
    for (const which of Object.keys(BACKEND) as (keyof typeof BACKEND)[]) {
      expect(existsSync(BACKEND[which]), `${which} 缺失：${BACKEND[which]}`).toBe(true);
    }
  });

  it('承流面：类级前缀 × 子路径 拼出的属主 URL = 前端族根常量', () => {
    const prefixes = classLevelPrefixes(readJava('controller'));
    const owner = prefixes.find((p) => p.startsWith('/api/'));
    expect(owner, '类级 @RequestMapping 里没有带 /api 的属主前缀').toBeDefined();
    const subPaths = [...readJava('controller').matchAll(/@PostMapping\("([^"]+)"\)/g)].map((m) => m[1]);
    expect(subPaths).toContain(OWNER_SUB_PATH);
    // 前端实际打出去的那条腿＝族根 + 子路径，必须与后端拼出来的逐字相等
    expect(`${owner}${OWNER_SUB_PATH}`).toBe(`${DATANET_DATASOURCE_BASE}${OWNER_SUB_PATH}`);
    expect(fetchUrlOfPreviewLeg()).toBe(`${owner}${OWNER_SUB_PATH}`);
  });

  it('响应面：后端 previewSchemaByConfig 写的键集 = 契约七键，旧折叠键面不得回流', () => {
    const slice = javaMethodSlice(
      readJava('service'),
      'public Map<String, Object> previewSchemaByConfig(',
    );
    const written = mapPutKeys(slice, 'result');
    expect(written).toEqual([...BACKEND_RESPONSE_KEYS].sort());
    // `objects`/`columnPreview` 是按 ID 那法的键面：混进本腿＝两侧又各说各话
    expect(written).not.toContain('objects');
    expect(written).not.toContain('columnPreview');
    expect(written).not.toContain('data');
  });

  it('列面：后端逐列写的键集 = {name,type,required,comment}，与前端解析认的四键一致', () => {
    const slice = javaMethodSlice(
      readJava('service'),
      'public Map<String, Object> previewSchemaByConfig(',
    );
    expect(mapPutKeys(slice, 'field')).toEqual([...BACKEND_COLUMN_KEYS].sort());
  });

  it('请求体面：前端送的每个键都在后端 DTO 上有字段，且后端校验的两键前端必送', () => {
    const dtoSrc = readJava('dto');
    const fields = [...dtoSrc.matchAll(/\bprivate\s+\w+(?:<[^>]+>)?\s+(\w+)\s*;/g)].map((m) => m[1]);
    const sent = Object.keys(buildSchemaPreviewDto({
      type: 'mysql', host: 'h', port: 3306, username: 'u', password: 'p', database: 'db',
    })).sort();
    for (const key of sent) expect(fields, `DTO 无字段可接前端送的 "${key}"`).toContain(key);
    // 属主校验腿要求非空的两键，缺一即 400——前端必须始终送（同一 getter 出现多次只算一项）
    const validated = [...new Set([...javaMethodSlice(readJava('registry'), 'public Map<String, Object> previewSchemaRaw(')
      .matchAll(/dto\.get(\w+)\(\)/g)].map((m) => m[1][0].toLowerCase() + m[1].slice(1)))].sort();
    expect(validated).toEqual(['connectionConfig', 'datasourceType']);
    for (const key of validated) expect(sent, `后端必检 "${key}" 而前端未送`).toContain(key);
  });

  it('解析面认全七键：条件键缺省＝该面未给（null/false），不得折成空面或成功', () => {
    const full = parseSchemaPreviewResponse({
      type: 'MYSQL', tableNames: ['T1'], fields: [{ name: 'id', type: 'INT', required: true, comment: null }],
      fieldsObject: 'T1', fieldsDegraded: true, fieldsReason: '列面取数失败', note: null,
    });
    expect(full.ok).toBe(true);
    // 判别用 `in`：本仓 tsconfig 无 strict，`full.ok` 不参与类型缩窄
    if (!('value' in full)) throw new Error(`应解析成功，实得违约：${full.reason}`);
    expect(full.value).toEqual({
      ok: true, type: 'MYSQL', tableNames: ['T1'],
      fields: [{ name: 'id', type: 'INT', required: true, comment: null }],
      fieldsObject: 'T1', fieldsDegraded: true, fieldsReason: '列面取数失败', note: null,
    });
    const bare = parseSchemaPreviewResponse({ type: 'MYSQL', tableNames: [], fields: [] });
    expect(bare.ok).toBe(true);
    if (!('value' in bare)) throw new Error(`应解析成功，实得违约：${bare.reason}`);
    // 对象面为空且列面未降级：fieldsObject/fieldsDegraded/fieldsReason 全部留「未给」，与「降级」可分
    expect(bare.value.fieldsObject).toBeNull();
    expect(bare.value.fieldsDegraded).toBe(false);
    expect(bare.value.fieldsReason).toBeNull();
  });

  it('判据能变红：键名漂移／幻影路径／多送无主键，各自都被吃到（防恒绿）', () => {
    // ① 后端把 `fields` 改名回旧折叠键面 ⇒ 响应面判据必须不再相等
    const RENAMED = 'public Map<String, Object> previewSchemaByConfig(\n'
      + '  result.put("type", t); result.put("tableNames", a); result.put("objects", a);\n'
      + '  result.put("columnPreview", b); result.put("fieldsObject", o); result.put("fieldsDegraded", true);\n'
      + '  result.put("fieldsReason", r); result.put("note", n);\n'
      + '}\n';
    const renamedKeys = mapPutKeys(RENAMED, 'result');
    expect(renamedKeys).not.toEqual([...BACKEND_RESPONSE_KEYS].sort());
    expect(renamedKeys).toContain('objects');
    // ② 前端解析对键类型违约必须整单判失败，不吐半截成功面
    expect(parseSchemaPreviewResponse({ type: 'MYSQL', tableNames: ['T1'], fields: 'oops' }).ok).toBe(false);
    expect(parseSchemaPreviewResponse({ type: 'MYSQL', tableNames: ['T1'], fields: [{ name: 1 }] }).ok).toBe(false);
    // ③ 送一枚 DTO 没有的键 ⇒ DTO 字段表判据必须拒绝
    const phantomKeySent = ['datasourceName', 'datasourceType', 'connectionConfig', 'schemaPreviewMode'];
    const dtoFields = [...readJava('dto').matchAll(/\bprivate\s+\w+(?:<[^>]+>)?\s+(\w+)\s*;/g)].map((m) => m[1]);
    expect(phantomKeySent.filter((k) => !dtoFields.includes(k))).toEqual(['schemaPreviewMode']);
    // ④ 幻影路径判据：类级前缀里若没有属主前缀（＝改回 ecos/data 那族），承流面判据要能红
    const PHANTOM_CTRL = '@RequestMapping({"/api/v1/ecos/data/datasource", "/datanet/datasource"})';
    expect(classLevelPrefixes(PHANTOM_CTRL).some((p) => p === DATANET_DATASOURCE_BASE)).toBe(false);
  });
});

/**
 * 从前端属主腿源码里把真正打出去的那条 URL 读回来（而不是凭用例里重写一遍常量）。
 * 重写一遍＝两侧都由测试自己保证，生产代码漂了门还绿。
 */
function fetchUrlOfPreviewLeg(): string {
  const src = readFileSync(
    fileURLToPath(new URL('../datasource.ts', import.meta.url)),
    'utf8',
  );
  const m = /post<unknown>\(`\$\{DATANET_DATASOURCE_BASE\}([^`]*)`/.exec(src);
  expect(m, 'datasource.ts 里预览腿不再从属主常量拼 URL（幻影或私设基址回流）').not.toBeNull();
  return `${DATANET_DATASOURCE_BASE}${(m as RegExpExecArray)[1]}`;
}
