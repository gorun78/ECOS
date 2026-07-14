/**
 * gitService — Git API service layer for the v2 PMO data-workbench
 *
 * All 8 API functions take repoId as the first parameter per the v2 spec.
 * Uses apiFetchData from ../api for all HTTP calls (auto-extracts .data, handles auth).
 *
 * Endpoints:
 *   GET  /api/v1/ecos/git/status?repoId={repoId}
 *   GET  /api/v1/ecos/git/commits?repoId={repoId}&limit=N
 *   POST /api/v1/ecos/git/commit?repoId={repoId}
 *   GET  /api/v1/ecos/git/branches?repoId={repoId}
 *   GET  /api/v1/ecos/git/diff?repoId={repoId}&commit1=X&commit2=Y
 *   POST /api/v1/ecos/git/tag?repoId={repoId}
 *   POST /api/v1/ecos/git/rollback?repoId={repoId}
 *   POST /api/v1/ecos/git/branch?repoId={repoId}
 *
 * @license Apache-2.0
 */

import { apiFetchData } from '../api';

// ── Helpers ─────────────────────────────────────────────────

function q(repoId: string, extras?: Record<string, string | number>): string {
  const params = new URLSearchParams();
  params.set('repoId', encodeURIComponent(repoId));
  if (extras) {
    Object.entries(extras).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') {
        params.set(k, encodeURIComponent(String(v)));
      }
    });
  }
  return '?' + params.toString();
}

// ── API Functions ───────────────────────────────────────────

/** GET /api/v1/ecos/git/status?repoId={repoId} */
export function fetchStatus<T = any>(repoId: string): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/status${q(repoId)}`);
}

/** GET /api/v1/ecos/git/commits?repoId={repoId}&limit=N&path=P */
export function fetchCommits<T = any>(
  repoId: string,
  path?: string,
  limit?: number,
): Promise<T> {
  return apiFetchData<T>(
    `/api/v1/ecos/git/commits${q(repoId, { path, limit })}`,
  );
}

/** POST /api/v1/ecos/git/commit?repoId={repoId} */
export function commit<T = any>(repoId: string, data?: any): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/commit${q(repoId)}`, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
    headers: data ? { 'Content-Type': 'application/json' } : undefined,
  });
}

/** GET /api/v1/ecos/git/branches?repoId={repoId} */
export function fetchBranches<T = any>(repoId: string): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/branches${q(repoId)}`);
}

/** GET /api/v1/ecos/git/diff?repoId={repoId}&commit1=X&commit2=Y&path=P */
export function fetchDiff<T = any>(
  repoId: string,
  commit1: string,
  commit2: string,
  path?: string,
): Promise<T> {
  return apiFetchData<T>(
    `/api/v1/ecos/git/diff${q(repoId, { commit1, commit2, path })}`,
  );
}

/** POST /api/v1/ecos/git/tag?repoId={repoId} */
export function createTag<T = any>(repoId: string, data?: any): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/tag${q(repoId)}`, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
    headers: data ? { 'Content-Type': 'application/json' } : undefined,
  });
}

/** POST /api/v1/ecos/git/rollback?repoId={repoId} */
export function rollback<T = any>(repoId: string, data?: any): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/rollback${q(repoId)}`, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
    headers: data ? { 'Content-Type': 'application/json' } : undefined,
  });
}

/** POST /api/v1/ecos/git/branch?repoId={repoId} */
export function createBranch<T = any>(repoId: string, data?: any): Promise<T> {
  return apiFetchData<T>(`/api/v1/ecos/git/branch${q(repoId)}`, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
    headers: data ? { 'Content-Type': 'application/json' } : undefined,
  });
}
