// E2E: Security Center → Detect tab → verify RLS/CLS data displays, ABAC evaluate works, masking works
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
const consoleErrors = [];
page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

try {
  // Login
  await page.goto('http://localhost:3000/#/login');
  await page.waitForLoadState('networkidle');
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // Go to Security Center
  await page.goto('http://localhost:3000/#/security-center');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click "安全策略" tab (security.detect = "安全策略")
  const detectTab = page.locator('button:has-text("安全策略")');
  await detectTab.first().click({ timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(1500);

  // ── Test 1: ABAC CRUD ──
  console.log('=== Test 0: ABAC CRUD ===');
  // First sub-tab should be ABAC策略管理 (abac-crud)
  const abacCrudRows = await page.locator('table tbody tr').count();
  const abacCrudText = abacCrudRows > 0 ? await page.locator('table tbody tr').first().textContent() : '(empty)';
  console.log(`  ABAC CRUD rows: ${abacCrudRows}, first: ${abacCrudText?.substring(0, 80)}`);
  const hasAbacCrud = abacCrudRows > 0;

  // ── Test 1: RLS Policy data displays ──
  console.log('=== Test 1: RLS Policy ===');
  const rlsTab = page.locator('button:has-text("RLS"), button:has-text("行级")').first();
  if (await rlsTab.count() > 0) {
    await rlsTab.click();
    await page.waitForTimeout(2000);
  }

  const tableRows = await page.locator('table tbody tr').count();
  const firstRowText = tableRows > 0 ? await page.locator('table tbody tr').first().textContent() : '(empty)';
  const rlsHasData = tableRows > 0;
  console.log(`  RLS rows: ${tableRows}, has data: ${rlsHasData}`);
  console.log(`  First row: ${firstRowText?.substring(0, 120)}`);

  // ── Test 2: CLS Policy data displays ──
  console.log('\n=== Test 2: CLS Policy ===');
  const clsTab = page.locator('button:has-text("CLS"), button:has-text("列级")').first();
  if (await clsTab.count() > 0) {
    await clsTab.click();
    await page.waitForTimeout(2000);
  }

  const clsRows = await page.locator('table tbody tr').count();
  const clsRowText = clsRows > 0 ? await page.locator('table tbody tr').first().textContent() : '(empty)';
  const clsHasData = clsRows > 0;
  console.log(`  CLS rows: ${clsRows}, has data: ${clsHasData}`);
  console.log(`  First row: ${clsRowText?.substring(0, 120)}`);

  // Check CLS has edit/delete buttons (SVG icons in action column)
  const clsActionIcons = await page.locator('table tbody tr').first().locator('svg').count();
  console.log(`  CLS action icons: ${clsActionIcons} (expect >=2 for edit+delete)`);

  // ── Test 3: ABAC Evaluate ──
  console.log('\n=== Test 3: ABAC Evaluate ===');
  const abacTab = page.locator('button:has-text("ABAC策略评估器"), button:has-text("ABAC Evaluator")').first();
  if (await abacTab.count() > 0) {
    await abacTab.click();
    await page.waitForTimeout(1000);
  }

  // Fill ABAC form
  await page.fill('input[placeholder="user_001"]', 'admin');
  await page.fill('input[placeholder="admin, analyst"]', 'admin');
  await page.fill('input[placeholder="table"]', 'td_user');
  await page.fill('input[placeholder="customer_data"]', 'td_user');

  // Click evaluate button
  const evalBtn = page.locator('button:has-text("执行评估"), button:has-text("Evaluate")').first();
  if (await evalBtn.count() > 0) {
    await evalBtn.click();
    await page.waitForTimeout(3000);
  }

  const pageText = await page.textContent('body');
  const hasAbacResult = pageText.includes('允许') || pageText.includes('拒绝') ||
    pageText.includes('Allow') || pageText.includes('Deny');
  console.log(`  ABAC result visible: ${hasAbacResult}`);

  // ── Test 4: Masking ──
  console.log('\n=== Test 4: Masking ===');
  const maskTab = page.locator('button:has-text("脱敏")').first();
  if (await maskTab.count() > 0) {
    await maskTab.click();
    await page.waitForTimeout(1000);
  }

  // Select PHONE mask type
  const phoneBtn = page.locator('button:has-text("手机号"), button:has-text("Phone")').first();
  if (await phoneBtn.count() > 0) {
    await phoneBtn.click();
    await page.waitForTimeout(500);
  }

  // Fill input
  const textarea = page.locator('textarea').first();
  if (await textarea.count() > 0) {
    await textarea.fill('13800138000');
  }

  // Click mask button (not the tab, the action button)
  const maskBtn = page.locator('button:has-text("执行脱敏"), button:has-text("Mask")').last();
  if (await maskBtn.count() > 0) {
    await maskBtn.click();
    await page.waitForTimeout(3000);
  }

  const maskPageText = await page.textContent('body');
  const hasMaskResult = maskPageText.includes('138****8000') || maskPageText.includes('****');
  console.log(`  Masking result visible: ${hasMaskResult}`);

  // ── Summary ──
  console.log('\n=== Summary ===');
  console.log(`ABAC CRUD: ${hasAbacCrud ? '✅' : '❌'}`);
  console.log(`RLS data: ${rlsHasData ? '✅' : '❌'}`);
  console.log(`CLS data: ${clsHasData ? '✅' : '❌'}`);
  console.log(`CLS edit/delete: ${clsActionIcons >= 2 ? '✅' : '❌'}`);
  console.log(`ABAC evaluate: ${hasAbacResult ? '✅' : '❌'}`);
  console.log(`Masking: ${hasMaskResult ? '✅' : '❌'}`);

  const corePass = hasAbacCrud && rlsHasData && clsHasData && clsActionIcons >= 2;
  console.log(`\n${corePass ? '✅ CORE PASS' : '❌ CORE FAIL'}`);

  if (consoleErrors.length > 0) {
    console.log('\nConsole errors:', consoleErrors.slice(0, 3));
  }

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
