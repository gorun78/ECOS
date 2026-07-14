/**
 * Glossary API Service Layer
 * Encapsulates all glossary term CRUD operations against the backend.
 * Backend returns ApiResponse<T>: { code: 0 (success), message, data }
 */

const API_BASE = '/api/glossary';

export interface GlossaryTerm {
  id: string;
  code: string | null;
  name: string;
  definition: string;
  domain: string;
  owner: string | null;
  status: string;
  createdBy: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface GlossaryListResult {
  items: GlossaryTerm[];
  total: number;
}

interface ApiResponse<T> {
  code: number;
  message?: string;
  data?: T;
  timestamp?: number;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${path}`, {
    headers,
    ...options,
  });

  if (!res.ok) {
    throw new Error(`Glossary API ${path} returned HTTP ${res.status}`);
  }

  const body = await res.json() as ApiResponse<T>;

  if (body.code && body.code !== 0) {
    throw new Error(body.message || `API error code=${body.code}`);
  }

  return body.data as T;
}

/**
 * Fetch glossary terms with optional domain and status filters.
 */
export async function getGlossaryTerms(params?: {
  domain?: string;
  status?: string;
}): Promise<GlossaryListResult> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  const query = qs.toString();
  const path = query ? `/terms?${query}` : '/terms';

  const data = await request<GlossaryTerm[]>(path);
  return { items: data ?? [], total: data?.length ?? 0 };
}

/**
 * Create a new glossary term.
 */
export async function createGlossaryTerm(data: {
  name: string;
  definition: string;
  domain?: string;
}): Promise<{ id: string }> {
  const term = await request<GlossaryTerm>('/terms', {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return { id: term.id };
}

/**
 * Update an existing glossary term.
 */
export async function updateGlossaryTerm(
  id: string,
  data: { name?: string; definition?: string; status?: string }
): Promise<void> {
  await request('/terms/' + id, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/**
 * Delete a glossary term.
 */
export async function deleteGlossaryTerm(id: string): Promise<void> {
  await request('/terms/' + id, {
    method: 'DELETE',
  });
}
