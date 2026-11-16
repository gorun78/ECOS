import fs from 'node:fs';
const T = fs.readFileSync('D:/tmp/tok.txt', 'utf8').trim();
const H = { Accept: 'application/json', Authorization: 'Bearer ' + T, 'Content-Type': 'application/json' };
const B = 'http://localhost:8080';
async function req(m, p, body) {
  const r = await fetch(B + p, { method: m, headers: H, body: body ? JSON.stringify(body) : undefined });
  let j = null; try { j = await r.json(); } catch { j = null; }
  return { p, m, s: r.status, j };
}
const line = (x, extra) => console.log(String(x.s).padEnd(3), x.m, x.p, extra === undefined ? '' : extra);

console.log('===== D132: 域表读权威 ecos_ontology.ecos_domain =====');
for (const p of ['/api/v1/ontology/domains', '/api/v1/ontology/domains/search?q=Fin']) {
  const x = await req('GET', p);
  const d = x.j && x.j.data;
  line(x, Array.isArray(d) ? `len=${d.length} :: ${d.map(e => e.code + '/' + e.id).join(',')}` : JSON.stringify(x.j).slice(0, 200));
}

console.log('\n===== D133: 空 ontologyId ⇒ 全量 =====');
for (const p of ['/api/v1/ontology/objects', '/api/v1/ontology/mappings/objects']) {
  const x = await req('GET', p);
  const d = x.j && x.j.data;
  line(x, Array.isArray(d) ? `len=${d.length} :: ${d.map(e => e.id).join(',')}` : JSON.stringify(x.j).slice(0, 200));
}

console.log('\n===== D135: 软删行不出现在读面 =====');
for (const p of ['/api/v1/ecos/ontologies/ont001/entities', '/api/v1/ecos/ontologies/entities/ent001',
                 '/api/v1/ecos/ontologies/entities/ent002/properties', '/api/v1/ecos/ontologies/entities/ent002/relationships',
                 '/api/v1/ecos/ontologies/relationships']) {
  const x = await req('GET', p);
  const d = x.j && x.j.data;
  line(x, Array.isArray(d) ? `len=${d.length} ids=${d.map(e => e.id).join(',')}` : (d ? 'obj' : JSON.stringify(x.j).slice(0,140)));
}

console.log('\n===== D136: 域实体端点按 domain_id 解析 =====');
const code = 'D13X_' + Date.now();
const c = await req('POST', '/api/v1/ecos/ontologies/domains/Finance/entities', { code, name: 'D136 复验对象', description: 'D136', entityType: 'MASTER' });
line(c, JSON.stringify(c.j && c.j.data));
const newId = c.j && c.j.data && c.j.data.id;
const g = await req('GET', '/api/v1/ecos/ontologies/domains/Finance/entities');
const gd = g.j && g.j.data;
line(g, Array.isArray(gd) ? `len=${gd.length} :: ${gd.map(e => e.id + '(' + e.domainId + ')').join(',')}` : JSON.stringify(g.j).slice(0,200));
console.log('   新对象按域可见:', Array.isArray(gd) && gd.some(e => e.code === code), ' newId=' + newId);
const g404 = await req('GET', '/api/v1/ecos/ontologies/domains/NoSuchDomain/entities');
line(g404, JSON.stringify(g404.j && (g404.j.message || g404.j.errorCode)).slice(0, 160));

console.log('\n===== D132 后半: 移域不再 409 =====');
if (newId) {
  const put = await req('PUT', `/api/v1/ontology/objects/${newId}/domain`, { domainCode: 'Procurement' });
  line(put, JSON.stringify(put.j && put.j.data).slice(0, 200));
  const putBad = await req('PUT', `/api/v1/ontology/objects/${newId}/domain`, { domainCode: 'NoSuchDomain' });
  line(putBad, JSON.stringify(putBad.j && (putBad.j.message || putBad.j.errorCode)).slice(0, 160));
  fs.writeFileSync('D:/tmp/d13x_id.txt', newId);
}

console.log('\n===== D134: 关系归档用 update_time（真实删一条） =====');
const rc = await req('POST', '/api/v1/ecos/ontologies/entities/ent002/relationships', {
  targetEntityId: 'ent003', code: 'D134P_' + Date.now(), name: 'D134 复验关系', relationType: 'ASSOCIATION',
});
const relId = rc.j && rc.j.data && rc.j.data.id;
line(rc, 'relId=' + relId);
if (relId) {
  const before = await req('GET', '/api/v1/ecos/ontologies/entities/ent002/relationships');
  const bd = before.j && before.j.data;
  console.log('   before del len=', Array.isArray(bd) ? bd.length : '?');
  const del = await req('DELETE', `/api/v1/ecos/ontologies/entities/ent002/relationships/${relId}`);
  line(del, JSON.stringify(del.j && (del.j.data || del.j.message)).slice(0, 160));
  const after = await req('GET', '/api/v1/ecos/ontologies/entities/ent002/relationships');
  const ad = after.j && after.j.data;
  console.log('   after del len=', Array.isArray(ad) ? ad.length : '?', ' relId仍在=', Array.isArray(ad) && ad.some(r => r.id === relId));
  const del2 = await req('DELETE', `/api/v1/ecos/ontologies/entities/ent002/relationships/${relId}`);
  line(del2, '幂等二次删 ' + JSON.stringify(del2.j && (del2.j.errorCode || del2.j.message)).slice(0, 120));
}
