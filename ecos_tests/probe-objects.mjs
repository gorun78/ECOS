#!/usr/bin/env node
// 排查 T4: 探测可用 entityCode 与字段
const BASE = "http://localhost:8080";
async function api(method, path, { body, token, tenant } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (tenant) headers["X-Tenant-Id"] = tenant;
  const res = await fetch(`${BASE}${path}`, {
    method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json; try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 300) }; }
  return { status: res.status, body: json };
}

async function main() {
  // 1) login
  const login = await api("POST", "/api/v1/auth/login", {
    body: { username: "admin", password: "admin123" },
  });
  const token = login.body?.data?.accessToken;
  console.log("login:", login.status, "token?", !!token);
  if (!token) return;

  // 2) 探测 gateway 是否有 /api/v1/ecos/objects/{code}/schema 端点
  const codes = ["Customer", "Supplier", "Project", "Order", "Product", "Asset", "Equipment", "RoadSection", "Inspection", "Alert", "Invoice", "FinanceTarget", "Facility", "ontology-001"];
  console.log("\n=== schema probe ===");
  for (const c of codes) {
    const s = await api("GET", `/api/v1/ecos/objects/${encodeURIComponent(c)}/schema`, { token, tenant: "tenant-a" });
    const props = s.body?.data?.properties || [];
    if (s.status === 200 && props.length > 0) {
      console.log(`OK ${c} (${props.length} cols): ${props.map(p => p.code).slice(0, 12).join(",")}`);
    } else {
      console.log(`   ${c} status=${s.status} msg=${(s.body?.message || s.body?.error || JSON.stringify(s.body).slice(0, 100))}`);
    }
  }

  // 3) 探测对象列表是否任意 1 个就有
  console.log("\n=== list probe (which entity exists) ===");
  for (const c of codes) {
    const l = await api("GET", `/api/v1/ecos/objects/${encodeURIComponent(c)}?page=1&size=1`, { token, tenant: "tenant-a" });
    if (l.status === 200) {
      const d = l.body?.data || {};
      const total = d.total ?? "?";
      console.log(`OK ${c} total=${total}`);
    } else {
      console.log(`   ${c} status=${l.status}`);
    }
  }

  // 4) 试创建 Customer (如果 schema 存在), 看实际错误
  const cs = await api("GET", "/api/v1/ecos/objects/Customer/schema", { token, tenant: "tenant-a" });
  if (cs.status === 200) {
    console.log("\n=== Customer create attempt ===");
    const props = (cs.body?.data?.properties || []).map(p => p.code);
    const idProbes = NEW_OBJECT_ID + "-probe-" + Date.now().toString(36).slice(-4);
    const body = { id: idProbes, name: "probe", status: "Draft" };
    const r = await api("POST", "/api/v1/ecos/objects/Customer", { token, tenant: "tenant-a", body });
    console.log("POST Customer:", r.status, JSON.stringify(r.body).slice(0, 300));
  }
}
main().catch(e => { console.error(e); process.exit(1); });
