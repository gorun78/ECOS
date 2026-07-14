import { test, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, '..', '.auth', 'user.json');
test.use({ storageState: AUTH_FILE });

test('Data Catalog loads with cards', async ({ page }) => {
  await page.goto('/#/data_catalog');
  await page.waitForTimeout(3000);
  
  // Should have data asset cards or table rows
  const cards = page.locator('.card, [class*="Card"], [class*="asset"], table tbody tr');
  const count = await cards.count();
  console.log(`Data Catalog: ${count} items found`);
  expect(count).toBeGreaterThan(0);
});

test('Ontology Explorer loads entities', async ({ page }) => {
  await page.goto('/#/ontology_explorer');
  await page.waitForTimeout(3000);
  
  // Check for entity list or tree
  const entities = page.locator('text=Entity, text=Property, [class*="entity"], [class*="ontology"]');
  const visible = await entities.first().isVisible().catch(() => false);
  console.log(`Ontology Explorer: entities visible = ${visible}`);
  expect(visible).toBe(true);
});

test('Mission Control loads dashboard', async ({ page }) => {
  await page.goto('/#/mission_control');
  await page.waitForTimeout(3000);
  
  // Should have some stats/metrics visible
  const bodyText = await page.textContent('body');
  expect(bodyText.length).toBeGreaterThan(200);
});

test('Biz Dashboard loads business data', async ({ page }) => {
  await page.goto('/#/biz_dashboard');
  await page.waitForTimeout(3000);
  
  const bodyText = await page.textContent('body');
  expect(bodyText.length).toBeGreaterThan(100);
});

test('Dataset Explorer loads with params', async ({ page }) => {
  await page.goto('/#/dataset_explorer/test_dataset');
  await page.waitForTimeout(3000);
  
  const bodyText = await page.textContent('body');
  expect(bodyText.length).toBeGreaterThan(100);
});

test('IAM Users page loads user table', async ({ page }) => {
  await page.goto('/#/iam_users');
  await page.waitForTimeout(3000);
  
  const bodyText = await page.textContent('body');
  expect(bodyText.length).toBeGreaterThan(100);
});
