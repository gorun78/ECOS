import { expect, test } from '@playwright/test';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { creds, loginAsAdmin } from '../helpers/ecos';

/**
 * CLI 门禁（ADR-21 §3 验收标识 cli-l3-approval.spec.ts）· 2026-11-09 CLI-MVP 批次
 *
 * 覆盖三件事：
 * 1) **只读守卫**：命令清单中不得出现 accessMode=WRITE；一旦出现，必须同时具备
 *    --approval-token 与服务端 ABAC 复核（当前未建设 ⇒ 出现即红，不是空转断言）。
 * 2) **活体命令行**：`ecos doctor` / `cognition models list` 经 gateway:8080 真跑，
 *    输出必须符合六字段契约与五值 status；依赖不可用时只能报 FAIL/BLOCKED/UNAVAILABLE。
 * 3) **凭据不回显**：stdout/stderr 不得出现口令或 token 明文（对齐 credentials-never-echo 口径）。
 *
 * 前置：gateway :8080 已启动；`tools/ecos-cli/target/ecos-cli.jar` 已构建
 *      （mvn -o -f tools/ecos-cli/pom.xml package）；凭据只从 .env 读。
 */

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..');
const MANIFEST = path.join(REPO_ROOT, 'tools', 'ecos-cli', 'src', 'main', 'resources', 'contract', 'ops.json');
const CLI_JAR = path.join(REPO_ROOT, 'tools', 'ecos-cli', 'target', 'ecos-cli.jar');

interface Manifest {
  operations: { operationId: string; method: string; path: string; status: string }[];
  commands: { name: string; accessMode: string; operationIds: string[]; flags: { flag: string; parameter: string }[] }[];
}

function manifest(): Manifest {
  expect(fs.existsSync(MANIFEST), `缺少 CLI 契约清单：${MANIFEST}`).toBeTruthy();
  return JSON.parse(fs.readFileSync(MANIFEST, 'utf8')) as Manifest;
}

function resolveJava(): string {
  const candidates: string[] = [];
  if (process.env.ECOS_JAVA) candidates.push(process.env.ECOS_JAVA);
  if (process.env.JAVA_HOME) {
    candidates.push(path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
  }
  // 本机工具链目录（~/env/jdk-*）：动态发现，不把机器绝对路径写进用例
  const envDir = path.join(os.homedir(), 'env');
  if (fs.existsSync(envDir)) {
    for (const name of fs.readdirSync(envDir).filter((n) => n.startsWith('jdk-'))) {
      candidates.push(path.join(envDir, name, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
    }
  }
  candidates.push('java');

  for (const candidate of candidates) {
    if (candidate !== 'java' && !fs.existsSync(candidate)) continue;
    const probe = spawnSync(candidate, ['-version'], { encoding: 'utf8', timeout: 20_000 });
    if (!probe.error && probe.status === 0) return candidate;
  }
  throw new Error(
    '未找到可用 JDK（JAVA_HOME 可能指向无效路径）。设置 ECOS_JAVA 或修正 JAVA_HOME 后重跑；CLI 需 JDK 17+。'
  );
}

let cachedJava: string | null = null;

function javaBin(): string {
  if (cachedJava === null) cachedJava = resolveJava();
  return cachedJava;
}

interface CliRun {
  status: number;
  stdout: string;
  stderr: string;
  json: any;
}

function runCli(args: string[], token?: string): CliRun {
  expect(
    fs.existsSync(CLI_JAR),
    `缺少 CLI jar：${CLI_JAR}\n先构建：mvn -o -f tools/ecos-cli/pom.xml package`
  ).toBeTruthy();
  const env = { ...process.env };
  if (token) env.ECOS_TOKEN = token;
  const res = spawnSync(javaBin(), ['-Dfile.encoding=UTF-8', '-jar', CLI_JAR, ...args], {
    cwd: REPO_ROOT,
    env,
    encoding: 'utf8',
    timeout: 120_000,
  });
  if (res.error) throw res.error;
  let json: any = null;
  try {
    json = JSON.parse(res.stdout ?? '');
  } catch {
    json = null;
  }
  return { status: res.status ?? -1, stdout: res.stdout ?? '', stderr: res.stderr ?? '', json };
}

test.describe('CLI 控制面（只读 MVP）', () => {
  test('命令清单只读守卫：无 WRITE 命令，operationId 均可解析', () => {
    const m = manifest();
    expect(m.commands.length).toBeGreaterThanOrEqual(3);
    const ids = new Set(m.operations.map((o) => o.operationId));
    for (const cmd of m.commands) {
      expect(cmd.accessMode, `命令 ${cmd.name} 必须是 READ；写命令需 --approval-token + 服务端 ABAC 复核`).toBe('READ');
      for (const id of cmd.operationIds) {
        expect(ids.has(id), `命令 ${cmd.name} 指向不存在的 operationId: ${id}`).toBeTruthy();
      }
      for (const flag of cmd.flags) {
        expect(flag.parameter, `flag ${flag.flag} 缺契约参数名`).toBeTruthy();
      }
    }
  });

  test('doctor：六字段 + 五值 status，且逐项检查不含 UNKNOWN 造假', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['doctor', '--output', 'json', '--timeout', '15000'], token);
    expect(run.json, `doctor 输出不是 JSON：${run.stderr.slice(0, 300)}`).toBeTruthy();

    expect(Object.keys(run.json).sort()).toEqual(['data', 'error', 'ok', 'source_refs', 'status', 'trace_id']);
    expect(['SUCCESS', 'FAILED', 'BLOCKED', 'UNAVAILABLE', 'PARTIAL']).toContain(run.json.status);
    const checks = run.json.data?.checks ?? [];
    expect(checks.length, 'doctor 必须有四项检查').toBe(4);
    for (const c of checks) {
      expect(['PASS', 'FAIL', 'BLOCKED', 'UNKNOWN']).toContain(c.status);
      expect(c.detail, `${c.name} 缺 detail（禁空默认值）`).toBeTruthy();
    }
    if (run.json.status === 'SUCCESS') {
      expect(checks.every((c: any) => c.status === 'PASS'), 'SUCCESS 必须全部 PASS').toBeTruthy();
      expect(run.status, 'SUCCESS 退出码必须为 0').toBe(0);
    } else {
      expect(run.status, '非 SUCCESS 不得返回 0 退出码').not.toBe(0);
    }
  });

  test('cognition models list：活体只读成功，且凭据不回显', async ({ request }) => {
    const { password } = creds();
    const token = await loginAsAdmin(request);
    const run = runCli(['cognition', 'models', 'list', '--output', 'json', '--model-kind', 'CM-03'], token);

    expect(run.json, `输出不是 JSON：${run.stderr.slice(0, 300)}`).toBeTruthy();
    expect(['SUCCESS', 'BLOCKED', 'UNAVAILABLE', 'FAILED']).toContain(run.json.status);
    if (run.json.status === 'SUCCESS') {
      expect(run.json.ok).toBe(true);
      expect(Array.isArray(run.json.data), 'data 应为模型行数组').toBeTruthy();
      expect(run.json.source_refs?.[0]?.operationId).toBe('listCognitiveModels');
      expect(run.status).toBe(0);
    }

    const combined = `${run.stdout}\n${run.stderr}`;
    expect(combined.includes(token), 'stdout/stderr 回显了 token').toBe(false);
    if (password) {
      expect(combined.includes(password), 'stdout/stderr 回显了口令').toBe(false);
    }
  });

  test('--dry-run 不发请求：doctor 全项 UNKNOWN 且无 source 调用副作用', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['doctor', '--dry-run', '--output', 'json'], token);
    expect(run.json?.status).toBe('SUCCESS');
    const checks = run.json.data?.checks ?? [];
    expect(checks.length).toBe(4);
    expect(checks.every((c: any) => c.status === 'UNKNOWN')).toBeTruthy();
  });
});
