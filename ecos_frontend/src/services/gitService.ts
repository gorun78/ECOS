/**
 * gitService — Git API service layer for the v2 PMO data-workbench
 *
 * 设计口径（《补充详设 R7~R11》§2.1 I-2 / §3.1 DI-3）：
 *   - 入参只认 `repositoryId`（禁 localPath/username/password/token）；
 *   - 全强类型 VO（禁 `any`，DI-3 判据 = tsc --noEmit 通过）。
 *
 * Endpoints（datanet GitController）:
 *   GET  /api/v1/ecos/git/status?repoId=
 *   GET  /api/v1/ecos/git/commits?repoId=&offset=&limit=
 *   POST /api/v1/ecos/git/commit?repoId=
 *   GET  /api/v1/ecos/git/branches?repoId=
 *   GET  /api/v1/ecos/git/diff?repoId=&from=&to=
 *   POST /api/v1/ecos/git/tag?repoId=
 *   POST /api/v1/ecos/git/rollback?repoId=
 *   POST /api/v1/ecos/git/branch?repoId=
 *
 * @license Apache-2.0
 */

import { apiFetchData } from '../api';
import { API_BASE } from './httpClient';

// ── 强类型 VO（与 datanet GitController 响应键逐一对应） ──────────────

/** 仓库标识：服务端注册表主键（六枚种子之一；由调用方从上下文取得，禁自由拼路径）。 */
export type RepositoryId =
  | 'dw-pipeline'
  | 'ontology-model'
  | 'kb-asset-tree'
  | 'cognitive-model'
  | 'agent-collab'
  | 'scenario-def'
  | (string & {});

/** 失败/未接入的统一三态（`not_available` = 通道未就绪，不是业务成功）。 */
export interface GitStatusReason {
  status?: string;
  reason?: string;
}

export interface GitRepoStatusVO extends GitStatusReason {
  repoId: string;
  repoName?: string;
  currentCommit?: string;
  branch?: string;
}

export interface GitCommitVO {
  id: string;
  commitId: string;
  message: string;
  author?: string;
  committedAt?: string;
}

export interface GitCommitsPageVO extends GitStatusReason {
  repoId: string;
  items: GitCommitVO[];
  offset: number;
  limit: number;
  hasMore: boolean;
  total?: number;
}

export interface GitDiffFileVO {
  changeType: string;
  oldPath: string;
  newPath: string;
}

export interface GitDiffVO extends GitStatusReason {
  repoId: string;
  from: string;
  to: string;
  filesChanged?: GitDiffFileVO[];
  changedCount?: number;
}

export interface GitCommitResultVO extends GitStatusReason {
  repoId: string;
  commitId?: string;
  message?: string;
  committedAt?: string;
}

export interface GitTagVO extends GitStatusReason {
  repoId: string;
  tag?: string;
  message?: string;
  createdAt?: string;
}

export interface GitRollbackVO extends GitStatusReason {
  repoId: string;
  rollbackTo: string;
}

export interface GitBranchesVO extends GitStatusReason {
  repoId: string;
  current?: string;
  branches: string[];
}

/** push/pull/通用动作的响应（后端未实现的腿必须显式 `not_available`，禁伪成功）。 */
export interface GitActionVO extends GitStatusReason {
  repoId?: string;
}

export interface GitCommitBody {
  message: string;
  authorName?: string;
  authorEmail?: string;
}

export interface GitTagBody {
  name: string;
  message?: string;
}

export interface GitRollbackBody {
  commitId: string;
}

export interface GitBranchBody {
  name: string;
}

export interface GitPushBody {
  message?: string;
}

// ── Helpers ─────────────────────────────────────────────────

function q(repoId: string, extras?: Record<string, string | number | undefined>): string {
  // W196 收口（报告 §4.8 P1）：URLSearchParams.set/toString 本身即做百分号编码，
  // 旧实现再套一层 encodeURIComponent → repoId 含 '/' 时变 `%252F`（双重编码），
  // 后端 URI decode 一次只得 `%2F`，10 个 Git 接口全数受影响。改为传原始值。
  const params = new URLSearchParams();
  params.set('repoId', repoId);
  if (extras) {
    Object.entries(extras).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') {
        params.set(k, String(v));
      }
    });
  }
  return '?' + params.toString();
}

function postJson<T>(url: string, data?: unknown): Promise<T> {
  return apiFetchData<T>(url, {
    method: 'POST',
    body: data === undefined ? undefined : JSON.stringify(data),
    headers: data === undefined ? undefined : { 'Content-Type': 'application/json' },
  });
}

// ── API Functions ───────────────────────────────────────────

/** GET /api/v1/ecos/git/status?repoId={repoId} */
export function fetchStatus(repoId: RepositoryId): Promise<GitRepoStatusVO> {
  return apiFetchData<GitRepoStatusVO>(`${API_BASE}/v1/ecos/git/status${q(repoId)}`);
}

/** GET /api/v1/ecos/git/commits?repoId={repoId}&offset=N&limit=N */
export function fetchCommits(
  repoId: RepositoryId,
  offset?: number,
  limit?: number,
): Promise<GitCommitsPageVO> {
  return apiFetchData<GitCommitsPageVO>(
    `${API_BASE}/v1/ecos/git/commits${q(repoId, { offset, limit })}`,
  );
}

/** POST /api/v1/ecos/git/commit?repoId={repoId} */
export function commit(repoId: RepositoryId, data?: GitCommitBody): Promise<GitCommitResultVO> {
  return postJson<GitCommitResultVO>(`${API_BASE}/v1/ecos/git/commit${q(repoId)}`, data);
}

/** GET /api/v1/ecos/git/branches?repoId={repoId} */
export function fetchBranches(repoId: RepositoryId): Promise<GitBranchesVO> {
  return apiFetchData<GitBranchesVO>(`${API_BASE}/v1/ecos/git/branches${q(repoId)}`);
}

/** GET /api/v1/ecos/git/diff?repoId={repoId}&from=X&to=Y */
export function fetchDiff(
  repoId: RepositoryId,
  from: string,
  to: string,
): Promise<GitDiffVO> {
  return apiFetchData<GitDiffVO>(
    `${API_BASE}/v1/ecos/git/diff${q(repoId, { from, to })}`,
  );
}

/** POST /api/v1/ecos/git/tag?repoId={repoId} */
export function createTag(repoId: RepositoryId, data?: GitTagBody): Promise<GitTagVO> {
  return postJson<GitTagVO>(`${API_BASE}/v1/ecos/git/tag${q(repoId)}`, data);
}

/** POST /api/v1/ecos/git/rollback?repoId={repoId} */
export function rollback(repoId: RepositoryId, data?: GitRollbackBody): Promise<GitRollbackVO> {
  return postJson<GitRollbackVO>(`${API_BASE}/v1/ecos/git/rollback${q(repoId)}`, data);
}

/** POST /api/v1/ecos/git/branch?repoId={repoId} */
export function createBranch(repoId: RepositoryId, data?: GitBranchBody): Promise<GitActionVO> {
  return postJson<GitActionVO>(`${API_BASE}/v1/ecos/git/branch${q(repoId)}`, data);
}

/** POST /api/v1/ecos/git/push?repoId={repoId} — C181/W199 补齐：GitPanel push 直 fetch 收口 */
export function push(repoId: RepositoryId, data?: GitPushBody): Promise<GitActionVO> {
  return postJson<GitActionVO>(`${API_BASE}/v1/ecos/git/push${q(repoId)}`, data);
}

/** POST /api/v1/ecos/git/pull?repoId={repoId} — C181/W199 补齐 */
export function pull(repoId: RepositoryId, data?: GitPushBody): Promise<GitActionVO> {
  return postJson<GitActionVO>(`${API_BASE}/v1/ecos/git/pull${q(repoId)}`, data);
}

/**
 * POST /api/v1/ecos/git/{action}?repoId={repoId} — 通用 POST 逃生口（C181/W199 单通道保留口）。
 * 白名单防注入：action 必须是小写字母+下划线标识（禁 `/`·`?`·`..`），防止 GitPanel 传入
 * 未预期动作名绕过本文件而直接改拉其它 /api/v1/ecos/git/* 端点。
 */
export function postAction(
  action: string,
  repoId: RepositoryId,
  data?: Record<string, unknown>,
): Promise<GitActionVO> {
  if (!/^[a-z][a-z0-9_]*$/.test(action)) {
    throw new Error(`gitService.postAction: illegal action "${action}" (^[a-z][a-z0-9_]*$ required)`);
  }
  return postJson<GitActionVO>(`${API_BASE}/v1/ecos/git/${action}${q(repoId)}`, data);
}
