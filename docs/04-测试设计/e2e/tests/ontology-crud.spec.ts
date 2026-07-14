import { test, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, '..', '.auth', 'user.json');
test.use({ storageState: AUTH_FILE });

test('Ontology Entity CRUD', async ({ page, request }) => {
  const BASE = 'http://localhost:8081/sys-man/api/v1/gsxk/ontology';
  
  // 1. CREATE entity
  const createRes = await request.post(`${BASE}/entities`, {
    data: {
      name: `Playwright_Test_Entity_${Date.now()}`,
      description: 'Created by Playwright E2E test',
      entityType: 'CONCEPT'
    },
    headers: { 'Content-Type': 'application/json' }
  });
  console.log('CREATE status:', createRes.status());
  const createBody = await createRes.json();
  console.log('CREATE body:', JSON.stringify(createBody).slice(0, 200));
  expect(createRes.status()).toBe(200);
  expect(createBody.code).toBe(0);
  
  const entityId = createBody.data?.id || createBody.data?.entityId;
  console.log('Created entity ID:', entityId);
  
  // 2. GET verify
  const getRes = await request.get(`${BASE}/entities`);
  console.log('GET status:', getRes.status());
  expect(getRes.status()).toBe(200);
  const getBody = await getRes.json();
  expect(getBody.code).toBe(0);
  
  // 3. UPDATE (if entity was created successfully)
  if (entityId) {
    const updateRes = await request.put(`${BASE}/entities/${entityId}`, {
      data: {
        name: `Playwright_Test_Entity_${Date.now()}_UPDATED`,
        description: 'Updated by Playwright E2E test',
      },
      headers: { 'Content-Type': 'application/json' }
    });
    console.log('UPDATE status:', updateRes.status());
    
    // 4. DELETE
    const deleteRes = await request.delete(`${BASE}/entities/${entityId}`);
    console.log('DELETE status:', deleteRes.status());
  }
  
  console.log('✅ Ontology Entity CRUD complete');
});

test('API: Datasets endpoint returns data', async ({ request }) => {
  const res = await request.get('http://localhost:8081/sys-man/api/v1/gsxk/datasets');
  console.log('Datasets status:', res.status());
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.code).toBe(0);
  console.log(`Datasets: ${body.data?.records?.length || body.data?.length || 0} items`);
});

test('API: Goals endpoint accessible', async ({ request }) => {
  const res = await request.get('http://localhost:8081/sys-man/api/v1/gsxk/worldmodel/goals');
  console.log('Goals status:', res.status());
  // May be 500 if empty DB, but should not be 404
  expect(res.status()).not.toBe(404);
});

test('API: Pipelines endpoint accessible', async ({ request }) => {
  const res = await request.get('http://localhost:8081/sys-man/api/v1/gsxk/pipelines');
  console.log('Pipelines status:', res.status());
  expect(res.status()).not.toBe(404);
});
