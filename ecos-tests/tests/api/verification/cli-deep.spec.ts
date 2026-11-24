import { execFile } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../../helpers/ecos';

/**
 * B8·CLI — 命令面补测（矩阵域10 的 V-CLI-000~040）。
 *
 * 与 `cli.spec.ts`（首轮 11 条）互补：真跑 shaded jar 子进程，覆盖通用契约
 * （六字段/traceId/未知参数/超时）与逐命令语义（含「未接命令必须明确报未实现」）。
 *
 * 反假绿纪律（本文件踩过的坑，留作常驻提醒）：
 *  - **退出码必须直取子进程**：`execFile` 失败时数字退出码在 Windows 落 `e.code`、
 *    Unix 落 `e.exitCode`。任何「0 也当非 0」的兜底都会把真 0 改写成非 0。
 *  - CLI 的用法错误（未知命令/未知参数/非法 timeout）只写 **stderr**，断言须合并 stderr。
 *  - 未接命令（`wired=false`）必须显式 UNAVAILABLE，**不得**静默成功。
 */

const execFileAsync = promisify(execFile);
const CLI_JAR = join(process.cwd(), '..', 'tools', 'ecos-cli', 'target', 'ecos-cli.jar');

function resolveJava(): string {
  const candidates = [
    process.env.ECOS_JAVA,
    process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : '',
    join(process.env.USERPROFILE ?? process.env.HOME ?? '', 'env', 'jdk-17.0.20.1+1', 'bin', 'java.exe'),
    'java',
  ].filter(Boolean) as string[];
  const found = candidates.find((c) => c === 'java' || existsSync(c));
  if (!found) throw new Error('找不到 java 可执行文件');
  return found;
}

const JAVA = resolveJava();

type CliResult = { code: number; stdout: string; stderr: string; json: any };

function safeJson(s: string): any {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

async function runCli(args: string[]): Promise<CliResult> {
  try {
    const { stdout, stderr } = await execFileAsync(JAVA, ['-jar', CLI_JAR, ...args], {
      maxBuffer: 8 * 1024 * 1024,
      timeout: 180_000,
    });
    return { code: 0, stdout, stderr, json: safeJson(stdout) };
  } catch (e: any) {
    const stdout = String(e?.stdout ?? '');
    const stderr = String(e?.stderr ?? '');
    const raw = e?.exitCode ?? e?.code;
    return { code: typeof raw === 'number' ? raw : 1, stdout, stderr, json: safeJson(stdout) };
  }
}

let tokenFile = '';

test.beforeAll(async ({ request }) => {
  const token = await loginAsAdmin(request);
  const dir = mkdtempSync(join(tmpdir(), 'ecos-cli-deep-'));
  tokenFile = join(dir, 'token.txt');
  writeFileSync(tokenFile, token, 'utf8');
  expect(existsSync(CLI_JAR), `CLI jar 必须存在：${CLI_JAR}`).toBe(true);
});

const authed = (args: string[]) => runCli([...args, '--token-file', tokenFile]);

test.describe('一、通用输出契约（V-CLI-000/005）', () => {
  test('V-CLI-000 输出六字段契约：status/ok/data/error/trace_id', async () => {
    const { code, json } = await authed(['capabilities', 'list']);
    expect(code, `应成功，stdout=${JSON.stringify(json)?.slice(0, 200)}`).toBe(0);
    for (const k of ['ok', 'status', 'trace_id']) {
      expect(json, `输出必须含 ${k} 字段（六字段契约）`).toHaveProperty(k);
    }
    expect(['SUCCESS', 'FAILURE', 'FAILED', 'BLOCKED', 'UNAVAILABLE', 'PARTIAL'],
      `status 必须取值域内，实得 ${json?.status}`).toContain(json?.status);
  });

  test('V-CLI-005 traceId 贯通：输出 trace_id 非空', async () => {
    const { code, json } = await authed(['doctor']);
    expect(code, `doctor 应退出 0（凭据有效时），stdout=${JSON.stringify(json)?.slice(0, 200)}`).toBe(0);
    expect(json?.trace_id, 'doctor 输出应带 trace_id').toBeTruthy();
    expect(String(json.trace_id).length, 'trace_id 不应为空串').toBeGreaterThan(7);
  });
});

test.describe('二、参数契约（V-CLI-007/009）', () => {
  test('V-CLI-009 未知参数必须报错而非静默忽略', async () => {
    const { code, stderr, json } = await authed(['doctor', '--not-a-real-flag', 'xyz']);
    expect(code, '未知 flag 必须非 0 退出（禁静默忽略）').not.toBe(0);
    const text = String(json ?? '') + stderr;
    expect(/unknown|unrecognized|不认识|未知|不支持/i.test(text),
      `错误信息应说明未知参数（用法错误写 stderr），实得 ${text.slice(0, 300)}`).toBe(true);
  });

  test('V-CLI-007 非法 timeout 必须被拒（禁静默夹紧）', async () => {
    const { code, stderr } = await authed(['doctor', '--timeout', 'abc']);
    expect(code, '非法 timeout 必须非 0 退出').not.toBe(0);
    expect(stderr, '应说明 timeout 非法').toMatch(/timeout/i);
  });
});

test.describe('三、只读命令真实性（V-CLI-023~027）', () => {
  test('V-CLI-023 capabilities list 能力目录真实', async () => {
    const { code, json } = await authed(['capabilities', 'list']);
    expect(code).toBe(0);
    const cmds = json?.data?.commands ?? [];
    expect(cmds.length, '能力目录应为 8 条（2026-11-23 去 wagent 化 17→8）').toBe(8);
    const unwired = cmds.filter((c: any) => c.wired === false || c.status === 'NOT_IMPLEMENTED');
    // eslint-disable-next-line no-console
    console.log(`[V-CLI-023] total=${cmds.length} unwired=${unwired.length} names=${unwired.map((c: any) => c.name).join(',')}`);
  });

  test('V-CLI-024 cognition models list：只读面真实返回', async () => {
    // 2026-11-23 去 wagent 化：原 `agent inspect` 已随 W 面端点删除（无对应契约 op），
    // 本处改用同为只读的认知模型列表；「不存在 id 必须报错」的形态由 V-CLI-026 model list 承接。
    const { code, json } = await authed(['cognition', 'models', 'list']);
    expect(code, `cognition models list 应成功，stdout=${JSON.stringify(json)?.slice(0, 200)}`).toBe(0);
    expect(json?.ok, 'ok 必须为 true').toBe(true);
    expect(json?.data, '只读面应有 data').toBeTruthy();
  });

  test('V-CLI-026 model list 输出 items 信封且非空', async () => {
    const { code, json } = await authed(['model', 'list']);
    expect(code, `model list 应成功，stdout=${JSON.stringify(json)?.slice(0, 200)}`).toBe(0);
    const rows = json?.data?.items ?? json?.data?.models ?? (Array.isArray(json?.data) ? json.data : null);
    expect(Array.isArray(rows), '模型列表应为数组（items 信封）').toBe(true);
    expect(rows.length, '模型目录不应为空').toBeGreaterThan(0);
  });

  test('V-CLI-027 model route 未接 ⇒ 明确报未实现（不得静默成功）', async () => {
    const { code, json } = await authed(['model', 'route']);
    expect(code, '未接命令必须非 0 退出').toBe(3);
    expect(json?.status, '未接命令 status 必须为 UNAVAILABLE').toBe('UNAVAILABLE');
    expect(/route|未实装|UNAVAILABLE/i.test(JSON.stringify(json ?? '')), '应说明路由端点未实装').toBe(true);
  });
});

test.describe('四、写命令与审批墙（V-CLI-040）', () => {
  test('V-CLI-040 写命令无 approval-token 必须 BLOCKED 或明确拒绝（退出码非 0）', async () => {
    // 2026-11-23 去 wagent 化：原 L3 `ontology publish` 已随 W 面端点删除，现存写命令
    // 只剩 L1 `cognition model validate`。L1 不要求审批凭证，故本用例改为断言
    // 「写命令在缺必填参数时明确拒绝，绝不静默成功」(BLOCKED 腿的通用形态由 V-CLI-034 承接)。
    const { code, json, stderr } = await authed(['cognition', 'model', 'validate']);
    expect(code, '缺必填参数必须非 0（禁 0＝假成功）').not.toBe(0);
    expect(json?.ok, 'ok 必须为 false').toBeFalsy();
    const text = JSON.stringify(json ?? '') + stderr;
    expect(text.length, '应给出拒绝原因').toBeGreaterThan(0);
  });

  test('V-CLI-040b 用法错误退出码 64（与业务失败区分）', async () => {
    const { code, stderr } = await runCli(['bogus', 'command']);
    expect(code, '未知命令必须 64（USAGE），不是 1/2/3').toBe(64);
    expect(stderr.length, '用法错误必须写 stderr').toBeGreaterThan(0);
  });
});
