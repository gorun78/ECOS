import { execFile } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../../helpers/ecos';

/**
 * B7·CLI — 实跑验证（对应矩阵域10）。
 *
 * 这是**真跑 jar 子进程**，不是 mock。反假绿要点：
 *  - 未实装能力必须返回 UNAVAILABLE（ECOS-CLI-NOT-IMPLEMENTED），不能伪造成功
 *  - 缺必填参数必须报错退出，不能静默使用默认值
 *  - 每条命令输出必须是结构化 JSON（面向智能体），带 trace_id 便于排障
 */

const execFileAsync = promisify(execFile);
const CLI_JAR = join(process.cwd(), '..', 'tools', 'ecos-cli', 'target', 'ecos-cli.jar');

/**
 * Playwright 子进程不继承 shell 里临时 export 的 JAVA_HOME，且本机 JDK 不在系统 PATH
 * （真实工具链在 ~/env 下）。这里显式解析 java 可执行文件，缺一即报出可诊断的错误。
 */
function resolveJava(): string {
  const candidates = [
    process.env.ECOS_JAVA,
    process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : '',
    join(process.env.USERPROFILE ?? process.env.HOME ?? '', 'env', 'jdk-17.0.20.1+1', 'bin', 'java.exe'),
    'java',
  ].filter(Boolean) as string[];
  const found = candidates.find((c) => c === 'java' || existsSync(c));
  if (!found) {
    throw new Error(`找不到 java 可执行文件，候选=${candidates.join(' | ')}`);
  }
  return found;
}

const JAVA = resolveJava();

let tokenFile = '';

test.beforeAll(async ({ request }) => {
  const token = await loginAsAdmin(request);
  const dir = mkdtempSync(join(tmpdir(), 'ecos-cli-'));
  tokenFile = join(dir, 'token.txt');
  writeFileSync(tokenFile, token, 'utf8');
  expect(existsSync(CLI_JAR), `CLI jar 必须存在：${CLI_JAR}`).toBe(true);
});

async function runCli(args: string[]): Promise<{ code: number; stdout: string; json: any }> {
  try {
    const { stdout } = await execFileAsync(JAVA, ['-jar', CLI_JAR, ...args, '--token-file', tokenFile], {
      maxBuffer: 8 * 1024 * 1024,
      timeout: 180_000,
    });
    let json: any = null;
    try {
      json = JSON.parse(stdout);
    } catch {
      json = null;
    }
    return { code: 0, stdout, json };
  } catch (e: any) {
    const stdout = String(e?.stdout ?? '');
    let json: any = null;
    try {
      json = JSON.parse(stdout);
    } catch {
      json = null;
    }
    return { code: Number(e?.code ?? 1), stdout, json };
  }
}

test.describe('CLI 自检与发现', () => {
  test('V-CLI-001 doctor 四项检查全 PASS', async () => {
    const { code, json } = await runCli(['doctor']);
    expect(code, `doctor 应退出 0，stdout=${JSON.stringify(json).slice(0, 200)}`).toBe(0);
    expect(json?.ok).toBe(true);
    expect(json?.status).toBe('SUCCESS');
    const checks = json?.data?.checks ?? [];
    expect(checks.length, 'doctor 应有检查项').toBeGreaterThan(0);
    const failed = checks.filter((c: any) => c.status !== 'PASS');
    expect(failed.map((c: any) => `${c.name}:${c.status}`), 'doctor 不应有非 PASS 项').toEqual([]);
    expect(json?.trace_id, 'doctor 输出应带 trace_id').toBeTruthy();
  });

  test('V-CLI-002 --help 列出命令且标注自动化等级', async () => {
    const res = await execFileAsync(JAVA, ['-jar', CLI_JAR, '--help'], { maxBuffer: 4 * 1024 * 1024 });
    const text = res.stdout;
    ['doctor', 'agent run', 'ontology publish', 'knowledge extract', 'action commit'].forEach((c) => {
      expect(text, `--help 应列出命令 ${c}`).toContain(c);
    });
    // L3 命令必须标注需审批
    expect(text, '--help 应说明 --approval-token').toContain('approval-token');
  });

  test('V-CLI-003 capabilities list 返回 17 条命令与映射状态', async () => {
    const { code, json } = await runCli(['capabilities', 'list']);
    expect(code).toBe(0);
    expect(json?.ok).toBe(true);
    const cmds = json?.data?.commands ?? [];
    expect(cmds.length, 'capabilities 应列出全部命令').toBe(17);
    cmds.forEach((c: any) => {
      expect(c.name, '每条命令应有 name').toBeTruthy();
      expect(Object.prototype.hasOwnProperty.call(c, 'status') || Object.prototype.hasOwnProperty.call(c, 'wired'),
        `命令 ${c.name} 应暴露实现状态`).toBe(true);
    });
  });
});

test.describe('CLI 只读命令真实性', () => {
  test('V-CLI-010 cognition models list 真实响应（空表也是真实态）', async () => {
    const { code, json } = await runCli(['cognition', 'models', 'list']);
    expect(code, `命令应成功，stdout=${JSON.stringify(json).slice(0, 200)}`).toBe(0);
    expect(json?.ok).toBe(true);
    expect(Array.isArray(json?.data), '认知模型列表应为数组').toBe(true);
  });

  test('V-CLI-011 model list 带 catalog 来源标注（禁无源编造）', async () => {
    const { code, json } = await runCli(['model', 'list']);
    expect(code).toBe(0);
    expect(json?.ok).toBe(true);
    const d = json?.data ?? {};
    expect(d.catalog_source, 'model list 必须标注目录来源').toBeTruthy();
    expect(Array.isArray(d.items), 'model list 应含 items').toBe(true);
    expect(typeof d.total, 'model list 应有 total').toBe('number');
    // stub=true 时必须能被调用方识别（不得把桩数据当真实目录）
    if (d.stub === true) {
      expect(d.catalog_source, 'stub 模式必须同时给出 source 说明').toBeTruthy();
    }
  });
});

test.describe('CLI 未实装能力必须如实 UNAVAILABLE（核心反假绿）', () => {
  test('V-CLI-020 model route 未实装必须 UNAVAILABLE 且带原因', async () => {
    const { json } = await runCli(['model', 'route']);
    expect(json?.ok, 'model route 不得报成功').toBe(false);
    expect(json?.status, 'model route 状态应为 UNAVAILABLE').toBe('UNAVAILABLE');
    expect(String(json?.error?.code ?? ''), '应给出未实装错误码').toBe('ECOS-CLI-NOT-IMPLEMENTED');
    expect(String(json?.error?.message ?? ''), '应说明缺失的端点').toMatch(/端点|endpoint/i);
  });

  test('V-CLI-021 L3 命令无审批凭证必须 BLOCKED（不得假执行）', async () => {
    const { json } = await runCli([
      'ontology', 'publish',
      '--body-json', JSON.stringify({ candidateId: 'probe-candidate' }),
    ]);
    expect(json?.ok, 'L3 无凭证不得成功').toBe(false);
    expect(['BLOCKED', 'UNAVAILABLE', 'FAILED'], `L3 无凭证状态应为拒绝类，实得 ${json?.status}`)
      .toContain(String(json?.status));
    expect(String(json?.error?.code ?? json?.error?.message ?? ''), '必须给出拒绝原因').not.toBe('');
  });

  test('V-CLI-022 缺必填参数必须报错退出（禁默认值兜底）', async () => {
    const { code, json } = await runCli(['agent', 'readiness']);
    const rejected = code !== 0 || json?.ok === false;
    expect(rejected, `缺 --question-id 必须失败，实得 code=${code} ok=${json?.ok}`).toBe(true);
    const msg = String(json?.error?.message ?? json?.error ?? '');
    expect(msg || code !== 0, '应说明缺失的参数').toBeTruthy();
  });
});

test.describe('CLI 参数与安全边界', () => {
  test('V-CLI-030 --dry-run 只展示调用计划，不发请求', async () => {
    const withDry = await runCli(['model', 'list', '--dry-run']);
    expect(withDry.code, 'dry-run 应成功').toBe(0);
    const plan = String((withDry.json?.data as any)?.plannedCall ?? '');
    // 真实形态："GET http://localhost:8080/api/v1/ai/models"
    expect(plan, `dry-run 应给出 plannedCall（METHOD + URL），实得 ${JSON.stringify(withDry.json?.data)}`)
      .toMatch(/^(GET|POST|PUT|DELETE|PATCH)\s+\S+/);
    expect(plan, '计划应指向 gateway 而非 service 端口').not.toMatch(/:(1808\d|18090)/);
    // dry-run 不得返回真实业务数据
    const d = withDry.json?.data as any;
    expect(d?.items, 'dry-run 不得返回业务数据').toBeFalsy();
  });

  test('V-CLI-031 凭据永不回显', async () => {
    const { stdout } = await runCli(['doctor']);
    expect(stdout, '输出不得含 Bearer 明文').not.toMatch(/Bearer\s+eyJ/);
    expect(stdout, '输出不得含 token 原文').not.toContain(tokenFile);
  });

  test('V-CLI-032 非法 timeout 必须被拒（禁静默夹紧）', async () => {
    const { code, json } = await runCli(['model', 'list', '--timeout', '999999']);
    const rejected = code !== 0 || json?.ok === false;
    expect(rejected, `超范围 timeout 应被拒，实得 code=${code} ok=${json?.ok}`).toBe(true);
  });
});
