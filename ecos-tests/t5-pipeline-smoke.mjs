/**
 * T5 smoke: gateway up at :8080 — verify:
 *  1. /api/v1/auth/login POST admin/admin123 returns bearer token
 *  2. /api/v1/pipeline/definitions list returns pipelines
 *  3. /api/v1/pipeline/definitions/{id} detail returns nodes (post-fix)
 *  4. /api/v1/engine/data/pipeline/git/versions/{id} returns array (empty expected pre-commit)
 */
const BASE = 'http://localhost:8080';
let TOKEN = '';
async function get(path) {
  const r = await fetch(BASE + path, { headers: { Authorization: `Bearer ${TOKEN}` } });
  const j = await r.json().catch(() => ({}));
  return { status: r.status, ok: r.ok, json: j };
}
async function main() {
  let outcomes = [];
  // (0) login
  const loginRes = await fetch(BASE + '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const loginJson = await loginRes.json().catch(() => ({}));
  const token = loginJson?.data?.token || loginJson?.data?.accessToken || '';
  console.log('LOGIN', loginRes.status, 'token:', token ? token.slice(0, 30) + '...' : '(none)', 'count:', loginJson?.data ? 'ok' : 'n/a');
  if (!token) {
    console.error('login failed — cannot continue. body:', JSON.stringify(loginJson).slice(0, 300));
    process.exit(1);
  }
  TOKEN = token;

  // (1) list
  const list = await get('/api/v1/pipeline/definitions');
  const listData = Array.isArray(list.json?.data) ? list.json.data : [];
  console.log('LIST', list.status, 'success:', list.json?.success, 'count:', listData.length);
  outcomes.push(['list.200', list.status === 200, list.status]);
  outcomes.push(['list.success', Boolean(list.json?.success), list.json?.success]);

  // (2) detail
  let id = listData[0]?.id;
  if (!id) {
    const c = await fetch(BASE + '/api/v1/pipeline/definitions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` },
      body: JSON.stringify({
        name: 'smoke-t5',
        description: 'T5 smoke',
        nodes: [{ id: 'n1', nodeId: 'n1', type: 'SOURCE_JDBC', config: { datasourceId: 'd' }, positionX: 10, positionY: 10 }],
        edges: [],
      }),
    });
    const cj = await c.json().catch(() => ({}));
    id = cj?.data?.id;
    console.log('CREATE', c.status, 'id:', id);
  }
  const detail = await get('/api/v1/pipeline/definitions/' + encodeURIComponent(id));
  const d = detail.json?.data || {};
  const n = Array.isArray(d.nodes) ? d.nodes.length : -1;
  console.log('DETAIL', detail.status, 'code:', detail.json?.code, 'id:', d.id, 'nodes:', n);
  outcomes.push(['detail.200', detail.status === 200, detail.status]);
  outcomes.push(['detail.id', d.id === id, d.id]);
  outcomes.push(['detail.nodes>=0', n >= 0, n]);

  // (3) git versions — new endpoint (T1 in this wave)
  const gv = await get('/api/v1/engine/data/pipeline/git/versions/' + encodeURIComponent(id));
  console.log('GIT-VERSIONS', gv.status, 'code:', gv.json?.code, 'data:', JSON.stringify(gv.json?.data));
  outcomes.push(['git.versions.200', gv.status === 200, gv.status]);
  outcomes.push(['git.versions.arr', Array.isArray(gv.json?.data), JSON.stringify(gv.json?.data)]);

  let fail = 0;
  for (const [name, ok, got] of outcomes) {
    if (!ok) fail++;
    console.log('  ' + (ok ? 'PASS' : 'FAIL') + ' ' + name + ' got=' + JSON.stringify(got));
  }
  process.exit(fail === 0 ? 0 : 1);
}
main().catch((e) => { console.error('smoke-fail:', e); process.exit(1); });
