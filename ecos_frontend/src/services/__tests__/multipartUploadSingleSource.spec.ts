// @vitest-environment node
/**
 * multipart 上传腿单源 + 分态用例（#143/B5-14，台账 §13.2.4 C2′ 注 ㉔）
 *
 * <p>**本域缺陷本体**（改前）：两条浏览器二进制上传腿各自私设传输——
 * `pages/data-workbench/api/unstructured.ts#uploadUnstructured` 写
 * `fetch('/api/v1/datanet/datalake/unstructured/upload', {headers: {...authOnlyHeaders()}})`
 * （基址字面量 + 域内身份拼接 + 裸 fetch 三洞同在），`pages/knowledge/services/govern.ts#uploadEvalSeed`
 * 写 `const token = getAuthToken(); fetch(url, {headers: token ? {Authorization} : {}})`
 * （把「有没有身份」的判断复制到域里）。两处都不是 `apiFetchData` 的对手：那两个通道会先把响应体
 * 读成 text/JSON，而上传腿要的是「带身份 + 不预读体 + 自己决定怎么解析（一条要
 * `res.json().catch()` 兜底、一条直接 `res.json()`）」，正是 `rawAuthedUrlFetch` 的定义面。</p>
 *
 * <p>**为什么这两条腿的合流必须"不补 Content-Type"**：单源底头**只有** Authorization，
 * 这两腿改前的头集合也恰好只有 Authorization（有 token 时）或空对象（无 token 时），
 * 逐键相等 ⇒ 零语义变化。反之一旦在腿上传 `Content-Type: application/json`，
 * 浏览器就不再自造 `multipart/form-data; boundary=…`，后端解不了 form——那是把上传打成 400。
 * 本 spec 把这条钉成可复核判据（腿切片内不得出现 Content-Type），而不是留在口头注释里。</p>
 *
 * <p>**本批唯一登记的语义差**（有意为之，不是疏漏）：合流后网络层失败经 PMO-43 归一为
 * `NetworkError`（文案由 `Failed to fetch` 变 `Network error: fetch …`）并广播一次
 * `ecos-network-down`。改前一条腿把异常吞成 `{ok:false, error}`、一条腿裸抛 `TypeError`，
 * 全局掉线横幅都不知道服务已不可达。成功面、URL、方法、body、状态码分派逐字不变。</p>
 *
 * <p>**证据边界**：源级 + 配置级 + 单测级证据；后端未起、未 curl、未 live 复验，
 * 不宣布这两条上传腿的真实应答面已验证。</p>
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError } from '../../services/httpClient';
import { uploadUnstructured } from '../../pages/data-workbench/api/unstructured';
import { uploadEvalSeed } from '../../pages/knowledge/services/govern';

// 与 `privateTransportRatchet` 同口径：本 spec 的 multipart 全仓扫描要遍历 src 全树，
// 必须用**文件级** setConfig 声明超时（per-test 第三参只护写它的那条用例）。
vi.setConfig({ testTimeout: 30_000 });

const SRC_UNSTRUCTURED = readFileSync(
  fileURLToPath(new URL('../../pages/data-workbench/api/unstructured.ts', import.meta.url)),
  'utf8',
);
const SRC_GOVERN = readFileSync(
  fileURLToPath(new URL('../../pages/knowledge/services/govern.ts', import.meta.url)),
  'utf8',
);
const SRC_HTTP = readFileSync(
  fileURLToPath(new URL('../../services/httpClient.ts', import.meta.url)),
  'utf8',
);
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);
const SRC_ROOT = fileURLToPath(new URL('../..', import.meta.url));

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization\s*[:=]/;
const IN_DOMAIN_IDENTITY = /(?:authOnlyHeaders|authHeaders|getAuthToken)\s*\(/;
const CHANNEL_CALL = /rawAuthedUrlFetch\s*\(/g;
const CHANNEL_TEST = /rawAuthedUrlFetch\s*\(/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 剥行注释（与棘轮门同口径：注释里的历史形态不参与判据）。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

/** 取某个导出函数体的源码切片（到下一个顶层 `export ` 或文件尾）。 */
function sliceOf(src: string, exportName: string): string {
  const start = src.indexOf(`export async function ${exportName}`);
  if (start < 0) throw new Error(`未找到导出函数 ${exportName}`);
  const rest = src.slice(start + `export async function ${exportName}`.length);
  const next = rest.search(/\nexport (?:async )?(?:function|const|interface|type)/);
  return next < 0 ? rest : rest.slice(0, next);
}

function walk(dir: string, out: string[]): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(full) && !/\.d\.ts$/.test(full)) out.push(full);
  }
  return out;
}

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  headers?: Record<string, string>;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? (spec.body === undefined ? '' : JSON.stringify(spec.body));
  const map = new Map<string, string>(
    Object.entries({ 'content-type': 'application/json', ...(spec.headers ?? {}) }).map(([k, v]) => [
      k.toLowerCase(),
      v,
    ]),
  );
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string): string | null => map.get(k.toLowerCase()) ?? null },
    text: () => Promise.resolve(text),
    // 忠实还原浏览器语义：非法 JSON 必须是 **rejected promise**，不是在 json() 调用点同步抛。
    // 手造对象若写成 `() => Promise.resolve(JSON.parse(text))`，JSON.parse 的抛点落在箭头函数
    // 体内 ⇒ 生产代码的 `res.json().catch(兜底)` 根本接不到，非 JSON 错误体会从内层兜底
    // 改走外层 catch，用例断的是兜底形状（本 spec 首跑即以「Unexpected token '<'」暴露这点）。
    json: (): Promise<unknown> => {
      try {
        return Promise.resolve(JSON.parse(text));
      } catch (e) {
        return Promise.reject(e instanceof Error ? e : new Error(String(e)));
      }
    },
  } as unknown as Response;
}

/**
 * 桩别判据用 `typeof === 'function'`：`makeRes` 返回的是手造应答对象（node 环境下并非
 * `Response` 实例），若用 `instanceof Response` 分派会把成功应答误投进抛错支。
 */
function stubFetch(resOrThrower: Response | (() => Error)) {
  const thrower = typeof resOrThrower === 'function' ? resOrThrower : null;
  const res = thrower === null ? resOrThrower : null;
  const spy = vi.fn(async (_input: string, _init?: RequestInit): Promise<Response> => {
    if (thrower) throw thrower();
    return res as Response;
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

function lastCall(spy: ReturnType<typeof stubFetch>) {
  const call = spy.mock.calls[spy.mock.calls.length - 1];
  const init = (call[1] ?? {}) as RequestInit;
  return {
    url: String(call[0]),
    method: init.method,
    body: init.body as FormData | undefined,
    headers: { ...(init.headers ?? {}) } as Record<string, string>,
  };
}

function storageStub(): Storage {
  const m = new Map<string, string>();
  return {
    getItem: (k: string): string | null => m.get(k) ?? null,
    setItem: (k: string, v: string): void => void m.set(k, v),
    removeItem: (k: string): void => void m.delete(k),
    clear: (): void => m.clear(),
    key: (_index: number): string | null => null,
    get length() {
      return m.size;
    },
  } as unknown as Storage;
}

let dispatched: Array<{ type: string; detail?: unknown }> = [];

function stubSession(token: string | null) {
  const ls = storageStub();
  if (token !== null) ls.setItem('token', token);
  dispatched = [];
  const win = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((ev: { type: string; detail?: unknown }): boolean => {
      dispatched.push({ type: ev.type, detail: ev.detail });
      return true;
    }),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', storageStub());
  vi.stubGlobal('window', win);
}

const fileStub = { name: 'a.pdf', size: 3, type: 'application/pdf' } as unknown as File;

const upMeta = { source: 'UI', docId: 'doc-1' };

beforeEach(() => {
  stubSession('tok-up');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('uploadUnstructured（DW 非结构化近源层上传腿，#143/B5-14 合流 rawAuthedUrlFetch）', () => {
  it('URL 仍等于改前字面量、方法不变、body 是同一 multipart 面', async () => {
    const spy = stubFetch(makeRes({ body: { code: 200, message: 'ok', data: { resourceId: 'r-1' } } }));

    const out = await uploadUnstructured(fileStub, upMeta);

    const call = lastCall(spy);
    expect(call.url).toBe('/api/v1/datanet/datalake/unstructured/upload');
    expect(call.method).toBe('POST');
    expect(call.body).toBeInstanceOf(FormData);
    expect(call.body!.get('file')).toBeDefined();
    expect(call.body!.get('req')).toBeDefined();
    expect(out).toEqual({ code: 200, message: 'ok', data: { resourceId: 'r-1' } });
  });

  it('头集合恰等于改前 `authOnlyHeaders()`：只有 Authorization，绝不新增 Content-Type（boundary 归浏览器）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 200, data: {} } }));

    await uploadUnstructured(fileStub, upMeta);

    const headers = lastCall(spy).headers;
    expect(headers).toEqual({ Authorization: 'Bearer tok-up' });
    expect(headers['Content-Type']).toBeUndefined();
    expect(headers['content-type']).toBeUndefined();
  });

  it('无 token 会话：头是空对象（改前 `authOnlyHeaders()` 即返回 `{}`，合流不许造出假身份头）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { code: 200, data: {} } }));

    await uploadUnstructured(fileStub, upMeta);

    expect(lastCall(spy).headers).toEqual({});
  });

  it('HTTP 非 2xx + JSON 错误体：沿用 `json.message`，形状与改前逐字一致', async () => {
    stubFetch(makeRes({ status: 400, body: { message: 'unsupported mime' } }));

    const out = await uploadUnstructured(fileStub, upMeta);

    expect(out).toEqual({ ok: false, error: 'unsupported mime' });
  });

  it('HTTP 非 2xx + 非 JSON 错误体：仍兜底成 `HTTP <status>`（网关 HTML/纯文本面不得塌成解析崩溃）', async () => {
    stubFetch(makeRes({ status: 500, raw: '<html>gateway boom</html>' }));

    const out = await uploadUnstructured(fileStub, upMeta);

    expect(out).toEqual({ ok: false, error: 'HTTP 500' });
  });

  it('网络层失败：按 PMO-43 归一并广播一次 ecos-network-down（本批登记的唯一语义差，仍返回 {ok:false}）', async () => {
    stubFetch(() => new TypeError('Failed to fetch'));

    const out = await uploadUnstructured(fileStub, upMeta);

    expect((out as { ok: boolean }).ok).toBe(false);
    expect((out as { error: string }).error).toContain('Network error');
    const downs = dispatched.filter((e) => e.type === 'ecos-network-down');
    expect(downs).toHaveLength(1);
  });
});

describe('uploadEvalSeed（KB 评测种子上传腿，#143/B5-14 合流 rawAuthedUrlFetch）', () => {
  it('URL 出自 KB_V1 单源基址、POST + file 字段，头等于改前手拼三元表达式', async () => {
    const spy = stubFetch(makeRes({ body: { data: { name: 'seeds.csv', count: 12 } } }));

    const out = await uploadEvalSeed(fileStub);

    const call = lastCall(spy);
    expect(call.url).toBe('/api/v1/knowledge/eval/seeds/upload');
    expect(call.method).toBe('POST');
    expect(call.body).toBeInstanceOf(FormData);
    expect(call.body!.get('file')).toBeDefined();
    expect(call.headers).toEqual({ Authorization: 'Bearer tok-up' });
    expect(out).toEqual({ name: 'seeds.csv', count: 12 });
  });

  it('无 token 会话：头是空对象（改前 `token ? {Authorization} : {}` 的另一支）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { name: 'x', count: 0 } }));

    const out = await uploadEvalSeed(fileStub);

    expect(lastCall(spy).headers).toEqual({});
    expect(out).toEqual({ name: 'x', count: 0 });
  });

  it('非 2xx 仍抛 `seed upload failed <status>`：分态文案不得被合流改写', async () => {
    stubFetch(makeRes({ status: 404, body: { message: 'no such endpoint' } }));

    await expect(uploadEvalSeed(fileStub)).rejects.toThrow('seed upload failed 404');
  });

  it('网络层失败：抛归一后的 NetworkError（改前是裸 TypeError），并广播一次全局掉线', async () => {
    stubFetch(() => new TypeError('Failed to fetch'));

    await expect(uploadEvalSeed(fileStub)).rejects.toBeInstanceOf(NetworkError);
    expect(dispatched.filter((e) => e.type === 'ecos-network-down')).toHaveLength(1);
  });
});

describe('两腿源级判据 + 全仓 multipart 面', () => {
  const LEGS = [
    { name: 'pages/data-workbench/api/unstructured.ts', src: SRC_UNSTRUCTURED, fn: 'uploadUnstructured' },
    { name: 'pages/knowledge/services/govern.ts', src: SRC_GOVERN, fn: 'uploadEvalSeed' },
  ];

  for (const leg of LEGS) {
    it(`${leg.name}：零私设传输、零域内身份拼接，且恰调用一次具名裸响应通道`, () => {
      const code = codeOnly(leg.src);
      expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
      expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
      expect(countMatches(code, IN_DOMAIN_IDENTITY)).toBe(0);
      expect(countMatches(code, CHANNEL_CALL)).toBe(1);
    });

    it(`${leg.fn} 腿切片内不出现 Content-Type（multipart boundary 的红线，补头即把上传打成 400）`, () => {
      const slice = codeOnly(sliceOf(leg.src, leg.fn));
      expect(slice).not.toMatch(/Content-Type/i);
      expect(slice).toMatch(CHANNEL_TEST);
    });
  }

  it('全仓 multipart 承体面清单可核对：每个 `new FormData(` 生产文件都走单源', () => {
    const files = walk(SRC_ROOT, [])
      .filter((f) => !/[\\/](?:__tests__|test)[\\/]/.test(f) && !/\.(?:test|spec)\.tsx?$/.test(f))
      .map((f) => ({ rel: relative(SRC_ROOT, f).split('\\').join('/'), src: readFileSync(f, 'utf8') }))
      .filter((f) => f.src.includes('new FormData('));
    const names = files.map((f) => f.rel).sort();
    expect(names).toEqual([
      'pages/data-workbench/api/unstructured.ts',
      'pages/knowledge/services/extract.ts',
      'pages/knowledge/services/govern.ts',
    ]);
    for (const f of files) {
      const code = codeOnly(f.src);
      expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
      expect(code).toMatch(CHANNEL_TEST);
    }
  });

  it('单源通道自身不设 Content-Type：`rawAuthedUrlFetch` 的底头只有 Authorization', () => {
    const slice = sliceOfChannel(SRC_HTTP, 'rawAuthedUrlFetch');
    expect(slice).toMatch(/getAuthToken\(\)/);
    expect(slice).not.toMatch(/Content-Type/i);
  });

  it('合成红：改前形态（基址字面量 + 域内拼身份 + 裸 fetch + 手补 Content-Type）必被抓住', () => {
    const bad = codeOnly(
      [
        "import { authOnlyHeaders } from '../../../services/auth';",
        'const res = await fetch("/api/v1/x/upload", {',
        "  method: 'POST',",
        "  headers: { ...authOnlyHeaders(), 'Content-Type': 'application/json' },",
        '  body: fd,',
        '});',
      ].join('\n'),
    );
    expect(countMatches(bad, PRIVATE_TRANSPORT)).toBe(1);
    expect(countMatches(bad, IN_DOMAIN_IDENTITY)).toBe(1);
    expect(countMatches(bad, CHANNEL_CALL)).toBe(0);
    expect(bad).toMatch(/Content-Type/i);
  });

  it('注释糊不住判据：历史形态写在注释里算绿，藏在代码里算红', () => {
    const prose = codeOnly(
      [
        "// 改前：const res = await fetch('/api/x', { headers: { ...authOnlyHeaders() } });",
        'const res = await rawAuthedUrlFetch(`${API_BASE}/x`, { method: "POST", body: fd });',
      ].join('\n'),
    );
    expect(countMatches(prose, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(prose, CHANNEL_CALL)).toBe(1);

    const hidden = codeOnly(
      'const res = await fetch(`${API_BASE}/x`, { method: "POST", body: fd }); // 已走单源',
    );
    expect(countMatches(hidden, PRIVATE_TRANSPORT)).toBe(1);
  });

  it('棘轮基线已剪除本批两文件，且元断言与自身条目实数一致（防「剪代码不剪基线」）', () => {
    expect(SRC_RATCHET).not.toContain("'pages/data-workbench/api/unstructured.ts'");
    expect(SRC_RATCHET).not.toContain("'pages/knowledge/services/govern.ts'");
    const baseline = SRC_RATCHET.slice(
      SRC_RATCHET.indexOf('const BASELINE'),
      SRC_RATCHET.indexOf('});', SRC_RATCHET.indexOf('const BASELINE')),
    );
    const entries = (baseline.match(/'[^']+':\s*\d+/g) ?? []).map((s) => Number(s.split(':')[1]));
    expect(entries.length).toBe(2);
    expect(entries.reduce((n, c) => n + c, 0)).toBe(2);
    expect(SRC_RATCHET).toContain('expect(entries.length).toBe(2)');
    expect(SRC_RATCHET).toContain('expect(entries.reduce((n, [, c]) => n + c, 0)).toBe(2)');
  });
});

/** 取 httpClient 内某通道的函数体切片（判据只看通道自身，不看调用方）。 */
function sliceOfChannel(src: string, fnName: string): string {
  const marker = `export async function ${fnName}`;
  const start = src.indexOf(marker);
  if (start < 0) throw new Error(`httpClient 未导出 ${fnName}`);
  const rest = src.slice(start + marker.length);
  const next = rest.search(/\n(?:export )?(?:async )?function /);
  return next < 0 ? rest : rest.slice(0, next);
}
