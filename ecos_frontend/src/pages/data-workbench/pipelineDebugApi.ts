/**
 * pipelineDebugApi — front-end only placeholder for the backend debug
 * session endpoints delivered by Wave3-upper.
 *
 *     GET  /api/v1/pipeline/debug/sessions
 *     GET  /api/v1/pipeline/debug/sessions/{id}
 *     POST /api/v1/pipeline/debug/sessions/{id}/start
 *     POST /api/v1/pipeline/debug/sessions/{id}/step-over
 *     POST /api/v1/pipeline/debug/sessions/{id}/continue
 *     POST /api/v1/pipeline/debug/sessions/{id}/stop
 *     POST /api/v1/pipeline/debug/sessions/{id}/reset
 *
 * The endpoint endpoints are registered behind the standard standard backend
 * CORS + auth (see data-engine T1); this layer just defines the typed
 * functions and systems so the UI can start interacting with them as soon
 * as the backend endpoints are live. No side-effect — only API functions.
 *
 * @license Apache-2.0
 */

const DBG_BASE = '/api/v1/pipeline/debug/sessions';

function authHeaders(): Record<string, string> {
  const token =
    typeof localStorage !== 'undefined'
      ? localStorage.getItem('token') || localStorage.getItem('accessToken') || ''
      : '';
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** A live (but still placeholder) debug session. */
export interface DebugSession {
  id: string;
  pipelineId: string;
  status: 'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPED';
  currentNodeId?: string;
  startedAt?: string;
  stoppedAt?: string;
}

/** Single breakpoint bound to one canvas node. */
export interface Breakpoint {
  id: string;
  nodeId: string;
  enabled: boolean;
  /** PB expression evaluated per row; empty = unconditional. */
  condition?: string;
}

async function call<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`debug-session ${method} ${path} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

/** GET /api/v1/pipeline/debug/sessions — list running sessions. */
export async function listDebugSessions(): Promise<DebugSession[]> {
  try {
    const data = await call<DebugSession[]>('GET', DBG_BASE);
    return Array.isArray(data) ? data : [];
  } catch (e) {
    console.warn('[pipeline-debug] listDebugSessions failed:', e);
    return [];
  }
}

/** GET /api/v1/pipeline/debug/sessions/{id} */
export async function getDebugSession(id: string): Promise<DebugSession | null> {
  try {
    return await call('GET', `${DBG_BASE}/${encodeURIComponent(id)}`);
  } catch (e) {
    console.warn('[pipeline-debug] getDebugSession failed:', e);
    return null;
  }
}

/** POST /api/v1/pipeline/debug/sessions/{id}/start */
export async function startDebugSession(id: string): Promise<{ ok: boolean; message?: string }> {
  try {
    return (await call(`POST` as 'GET' | 'POST', `${DBG_BASE}/${encodeURIComponent(id)}/start`, {})) as { ok: boolean; message?: string };
  } catch (e) {
    console.warn('[pipeline-debug] startDebugSession failed:', e);
    return { ok: false, message: e instanceof Error ? e.message : 'start failed' };
  }
}

/** POST /api/v1/pipeline/debug/sessions/{id}/step-over */
export async function stepOverDebugSession(id: string): Promise<{ ok: boolean; message?: string }> {
  try {
    return (await call('POST', `${DBG_BASE}/${encodeURIComponent(id)}/step-over`, {})) as { ok: boolean; message?: string };
  } catch (e) {
    console.warn('[pipeline-debug] stepOverDebugSession failed:', e);
    return { ok: false, message: e instanceof Error ? e.message : 'step-over failed' };
  }
}

/** POST /api/v1/pipeline/debug/sessions/{id}/continue */
export async function continueDebugSession(id: string): Promise<{ ok: boolean; message?: string }> {
  try {
    return (await call('POST', `${DBG_BASE}/${encodeURIComponent(id)}/continue`, {})) as { ok: boolean; message?: string };
  } catch (e) {
    console.warn('[pipeline-debug] continueDebugSession failed:', e);
    return { ok: false, message: e instanceof Error ? e.message : 'continue failed' };
  }
}

/** POST /api/v1/pipeline/debug/sessions/{id}/stop */
export async function stopDebugSession(id: string): Promise<{ ok: boolean; message?: string }> {
  try {
    return (await call('POST', `${DBG_BASE}/${encodeURIComponent(id)}/stop`, {})) as { ok: boolean; message?: string };
  } catch (e) {
    console.warn('[pipeline-debug] stopDebugSession failed:', e);
    return { ok: false, message: e instanceof Error ? e.message : 'stop failed' };
  }
}

/** POST /api/v1/pipeline/debug/sessions/{id}/reset */
export async function resetDebugSession(id: string): Promise<{ ok: boolean; message?: string }> {
  try {
    return (await call('POST', `${DBG_BASE}/${encodeURIComponent(id)}/reset`, {})) as { ok: boolean; message?: string };
  } catch (e) {
    console.warn('[pipeline-debug] resetDebugSession failed:', e);
    return { ok: false, message: e instanceof Error ? e.message : 'reset failed' };
  }
}