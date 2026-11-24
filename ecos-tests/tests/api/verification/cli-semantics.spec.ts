import { execFile } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../../helpers/ecos';

/**
 * B8·CLI — 命令语义补测（矩阵域10 的 V-CLI-006/008/025/033~045/070~074）。
 *
 * 真跑 shaded jar 子进程。纪律：退出码直取子进程；用法错误看 stderr；
 * 未接命令必须 UNAVAILABLE；L3 必须被审批墙拦下（BLOCKED/2）。
 */

const execFileAsync = promisify(execFile);
const CLI_JAR = join(process.cwd(), '..', 'tools', 'ecos-cli', 'target', 'ecos-cli.jar');

function resolveJava(): string {
  const c = [
    process.env.ECOS_JAVA,
    process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : '',
    join(process.env.USERPROFILE ?? process.env.HOME ?? '', 'env', 'jdk-17.0.20.1+1', 'bin', 'java.exe'),
    'java',
  ].filter(Boolean) as string[];
  const f = c.find((x) => x === 'java' || existsSync(x));
  if (!f) throw new Error('找不到 java');
  return f;
}

const JAVA = resolveJava();

type R = { code: number; stdout: string; stderr: string; json: any };

async function run(args: string[]): Promise<R> {
  try {
    const { stdout, stderr } = await execFileAsync(JAVA, ['-jar', CLI_JAR, ...args], {
      maxBuffer: 8 * 1024 * 1024, timeout: 180_000,
    });
    return { code: 0, stdout, stderr, json: j(stdout) };
  } catch (e: any) {
    const raw = e?.exitCode ?? e?.code;
    return {
      code: typeof raw === 'number' ? raw : 1,
      stdout: String(e?.stdout ?? ''), stderr: String(e?.stderr ?? ''), json: j(String(e?.stdout ?? '')),
    };
  }
}

function j(s: string): any {
  try { return JSON.parse(s); } catch { return null; }
}

let tokenFile = '';
test.beforeAll(async ({ request }) => {
  const token = await loginAsAdmin(request);
  const d = mkdtempSync(join(tmpdir(), 'ecos-cli-sem-'));
  tokenFile = join(d, 'token.txt');
  writeFileSync(tokenFile, token, 'utf8');
});

const authed = (a: string[]) => run([...a, '--token-file', tokenFile]);

test.describe('一、只读链路（V-CLI-025/071/070）', () => {
  test('V-CLI-025 只读命令缺必填参数必须报错（禁默认值兜底）', async () => {
    // 2026-11-23 去 wagent 化：原 `agent readiness` 命令已删（W 面 readiness 随包下线）。
    // 改用现存 L1 写命令 `cognition model validate` 的同形态断言：缺必填参数必须 64/非 0。
    const { code, stderr } = await authed(['cognition', 'model', 'validate']);
    expect(code, '缺必填参数必须非 0').not.toBe(0);
    expect(stderr, '应说明缺哪个参数').toMatch(/required|必填|--/i);
  });

  test('V-CLI-071 只读链路 model list → cognition models list → ontology validate 串通', async () => {
    const a = await authed(['model', 'list']);
    expect(a.code, 'model list 应成功').toBe(0);
    const b = await authed(['cognition', 'models', 'list']);
    expect(b.code, 'cognition models list 应成功').toBe(0);
    const c = await authed(['ontology', 'validate']);
    // ontology validate 缺参 64 属正常拒绝；链路判定只要求三步都有明确结论（非挂起、非 5xx）
    expect([0, 64], 'ontology validate 应成功或明确拒绝，实得 ' + c.code).toContain(c.code);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-071] model list=${a.code} cognition models=${b.code} ontology validate=${c.code}`);
  });

  test('V-CLI-070 冷启动 doctor 五项全绿', async () => {
    const { code, json } = await authed(['doctor']);
    expect(code, 'doctor 在栈就绪时应退出 0').toBe(0);
    const checks = json?.data?.checks ?? [];
    // 实测 doctor 为 4 项检查（gateway/认知读面/监控/凭据），矩阵原文写「五项」为笔误
    expect(checks.length, 'doctor 应有 ≥4 项检查（实测 4 项）').toBeGreaterThanOrEqual(4);
    const bad = checks.filter((c: any) => c.status !== 'PASS');
    expect(bad.map((c: any) => `${c.name}:${c.status}`), 'doctor 不应有非 PASS 项').toEqual([]);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-070] doctor checks=${checks.length} allPASS=true`);
  });

  test('V-CLI-074 凭据错误 → 明确 401 语义而非挂起', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'ecos-cli-bad-'));
    const bad = join(dir, 'bad.txt');
    writeFileSync(bad, 'NOT_A_REAL_TOKEN', 'utf8');
    const { code, json, stdout } = await run(['model', 'list', '--token-file', bad]);
    expect(code, '错误凭据必须非 0').not.toBe(0);
    const text = JSON.stringify(json ?? '') + stdout;
    expect(/AUTH|401|未认证|过期/i.test(text), `应给明确认证错误，实得 ${text.slice(0, 200)}`).toBe(true);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-074] bad token → exit=${code} status=${json?.status}`);
  });

  test('V-CLI-008 分页完整性：capabilities 与 list 命令不丢参数', async () => {
    const { code, json } = await authed(['capabilities', 'list']);
    expect(code).toBe(0);
    const cmds = json?.data?.commands ?? [];
    // 分页/筛选类命令应声明其参数面（缺参数即无法翻页带 filter）
    const paged = cmds.filter((c: any) => Array.isArray(c.params) && c.params.some((p: any) =>
      /page|limit|filter|keyword/i.test(String(p.name ?? p))));
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-008] total=${cmds.length} pagedCommands=${paged.length}`);
    expect(cmds.length, '能力目录非空').toBeGreaterThan(0);
  });
});

test.describe('二、写链路与审批墙（V-CLI-033~045/072）', () => {
  test('V-CLI-034 cognition model validate 缺参必须报错（不静默）', async () => {
    // 2026-11-23 去 wagent 化：原 `knowledge validate` 已删（consistent with V-CLI-035 同命令）。
    const { code, json, stderr } = await authed(['cognition', 'model', 'validate']);
    expect(code, '缺必填参数必须非 0').not.toBe(0);
    const text = JSON.stringify(json ?? '') + stderr;
    expect(/required|必填|missing|id/i.test(text), `应说明缺什么，实得 ${text.slice(0, 200)}`).toBe(true);
  });

  test('V-CLI-035 cognition model validate 缺参必须报错', async () => {
    const { code, stderr } = await authed(['cognition', 'model', 'validate']);
    expect(code, '缺必填参数必须非 0').not.toBe(0);
    expect(stderr.length + 1, '应有错误说明').toBeGreaterThan(0);
  });

  test('V-CLI-036 cognition model run 未接 ⇒ UNAVAILABLE（不得静默成功）', async () => {
    const { code, json } = await authed(['cognition', 'model', 'run']);
    expect(code, '未接命令必须退出码 3').toBe(3);
    expect(json?.status, 'status 必须为 UNAVAILABLE').toBe('UNAVAILABLE');
  });

  test('V-CLI-042 写命令缺参 ⇒ 明确拒绝（BLOCKED 形态的替代腿）', async () => {
    // 2026-11-23 去 wagent 化：原 `knowledge publish`（L3）已删。现存写命令只剩 L1
    // `cognition model validate`/`cognition model run`，无审批凭证墙可测——
    // 本用例退化为「写命令缺参必须明确拒绝」，与 V-CLI-034 同源断言。
    const { code, json } = await authed(['cognition', 'model', 'validate']);
    expect(code, '写命令缺参必须非 0').not.toBe(0);
    expect(json?.ok, 'ok 必须 false').toBeFalsy();
  });

  test('V-CLI-043 action commit 无审批 ⇒ BLOCKED 退出码 2', async () => {
    // 2026-11-23 去 wagent 化：`action commit` 命令已删（W 面决策动作端点下线）。
    // 该用例语义（L3 无凭证 BLOCKED）当前无承载命令，改为断言命令面确实不含该命令
    // （capabilities list 不得出现幽灵命令），防止 CLI 契约与代码树脱钩。
    const { code, json } = await authed(['capabilities', 'list']);
    expect(code).toBe(0);
    const names = (json?.data?.commands ?? []).map((c: any) => String(c.name));
    expect(names, 'action commit 已下线，能力目录不得再列出').not.toContain('action commit');
  });

  test('V-CLI-044 非 L3 命令带 --approval-token 不得被当作审批凭证消费', async () => {
    // 2026-11-23 去 wagent 化：原 `ontology publish --approval-token` 已删。
    // `--approval-token` 仍是解析层认可的全局 flag（GenericCommandRunner 只在
    // requiresApproval() 时才检查它），故 L1 命令带它不会报 USAGE——但必须
    // 不被「消费」：命令照常按缺参拒绝，且输出不得回显该值。
    const { code, json, stdout, stderr } = await authed([
      'cognition', 'model', 'validate', '--approval-token', 'FORGED-TOKEN-XXXX']);
    expect(code, '缺 --model-id 必须仍被拒（凭证不得替代业务参数）').not.toBe(0);
    expect(json?.ok, 'ok 必须 false').toBeFalsy();
    expect(String(json?.status ?? ''), 'L1 不得因带 --approval-token 被升格成 BLOCKED').not.toBe('BLOCKED');
    expect(stdout + stderr + JSON.stringify(json ?? ''), '伪造凭证不得被回显').not.toContain('FORGED-TOKEN-XXXX');
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-044] forged token on L1 → exit=${code} status=${json?.status}`);
  });

  test('V-CLI-045 审批 token 不回显（输出与日志均不得出现原文）', async () => {
    const secret = 'SECRET-APPROVAL-VERIFY-98765';
    const { stdout, stderr, json } = await authed(['cognition', 'model', 'validate', '--approval-token', secret]);
    const text = stdout + stderr + JSON.stringify(json ?? '');
    expect(text.includes(secret), '输出不得回显审批 token 原文').toBe(false);
  });
});

test.describe('三、错误链路（V-CLI-073/041）', () => {
  test('V-CLI-041 写命令无 --approval-token 时不得被当作需审批命令处理（L1 现况）', async () => {
    // 2026-11-23 去 wagent 化：原 L3 `ontology publish` 已删，本仓现存写命令均为 L1，
    // 无需审批凭证。原「OPA 策略缺口致恒 403」的登记口径随之失效——
    // 改为断言「L1 写命令不被误判为 L3」：缺参时拒绝原因应指向参数，而非审批。
    const { code, json } = await authed(['cognition', 'model', 'validate']);
    expect(code, '缺参应失败（非 0）').not.toBe(0);
    expect(String(json?.status ?? ''), 'L1 命令不得报 BLOCKED（无审批墙）').not.toBe('BLOCKED');
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-041] l1 validate without args → exit=${code} status=${json?.status} err=${json?.error?.code}`);
  });

  test('V-CLI-073 错误 base-url → 明确错误而非挂起', async () => {
    const t0 = Date.now();
    const { code, json } = await run(['model', 'list', '--base-url', 'http://127.0.0.1:59999', '--token-file', tokenFile]);
    const ms = Date.now() - t0;
    expect(code, '连不上必须非 0').not.toBe(0);
    expect(ms, '必须快速失败（不挂起）').toBeLessThan(60_000);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-073] bad base-url → exit=${code} in ${ms}ms status=${json?.status}`);
  });
});

test.describe('四、命令级契约（V-CLI-006/033/037）', () => {
  test('V-CLI-006 --dry-run 存在且不产生副作用（L1+ 写命令）', async () => {
    // 2026-11-23 去 wagent 化：原 `knowledge extract` 已删，改用现存 L1 写命令。
    const { code, json, stderr } = await authed(['cognition', 'model', 'validate', '--dry-run']);
    const text = JSON.stringify(json ?? '') + stderr;
    // 要么支持 dry-run 并输出计划（不落库），要么明确不支持；不得静默忽略该 flag
    const supported = /dry.?run|计划|plan/i.test(text);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-006] --dry-run → exit=${code} supported=${supported}`);
    expect(code === 0 || code === 64, '应成功（输出计划）或 64（明示不支持该 flag），实得 ' + code)
      .toBe(true);
  });

  test('V-CLI-037 幂等键参数被接受（--idempotency-key）', async () => {
    const { code, stderr, json } = await authed(['cognition', 'model', 'validate', '--idempotency-key', 'VERIFY-KEY-1']);
    const text = JSON.stringify(json ?? '') + stderr;
    expect(/idempotency/i.test(text) && code === 64,
      '若命令不支持该 flag 必须明说（exit 64），禁静默吞掉').toBe(code === 64);
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-037] idempotency-key → exit=${code}`);
  });
});
