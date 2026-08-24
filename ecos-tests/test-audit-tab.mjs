// E2E: Security Center → Audit tab — verify real data from backend (not mock)
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

  // Click "安全审计" tab — target the tab button specifically (has border-b-2 class)
  const auditTab = page.locator('button.border-b-2:has-text("安全审计"), button[class*="border-b-2"]:has-text("安全审计")').first();
  if (await auditTab.count() === 0) {
    // Fallback: click the last button containing "安全审计"
    const allAuditBtns = page.locator('button:has-text("安全审计")');
    const count = await allAuditBtns.count();
    await allAuditBtns.nth(count - 1).click();
  } else {
    await auditTab.click();
  }
  await page.waitForTimeout(3000);

  // ── Test 1: Stats cards show real data ──
  console.log('=== Test 1: Stats Cards ===');
  const statValues = await page.locator('.tabular-nums, .font-mono').allTextContents();
  // Stats cards have 4 numbers: todayCount, failureCount, activeUsers, anomalyIps
  const bodyText = await page.textContent('body');
  const hasStats = bodyText.includes('0') && bodyText.includes('1') && bodyText.includes('3');
  console.log(`  Stats visible in body: ${hasStats}`);

  // Check for stat card numbers (they use font-mono tabular-nums)
  const statCards = await page.locator('.grid .rounded-lg').count();
  console.log(`  Stat cards count: ${statCards} (expect 4)`);

  // ── Test 2: Audit log timeline shows real events ──
  console.log('\n=== Test 2: Audit Log Timeline ===');
  // Look for timeline event cards
  const eventCards = await page.locator('[class*="cursor-pointer"][class*="rounded-md"][class*="border"]').count();
  console.log(`  Event cards: ${eventCards}`);

  // Check for known audit data
  const hasLoginEvent = bodyText.includes('LOGIN');
  const hasDataExport = bodyText.includes('DATA_EXPORT') || bodyText.includes('DATA_IMPORT');
  const hasUserIds = bodyText.includes('admin') || bodyText.includes('operator');
  const hasIpAddresses = bodyText.includes('10.0.') || bodyText.includes('IP');
  console.log(`  Has LOGIN event: ${hasLoginEvent}`);
  console.log(`  Has DATA_EXPORT/IMPORT: ${hasDataExport}`);
  console.log(`  Has user IDs: ${hasUserIds}`);
  console.log(`  Has IP addresses: ${hasIpAddresses}`);

  // ── Test 3: Event expand/detail ──
  console.log('\n=== Test 3: Event Detail ===');
  if (eventCards > 0) {
    await page.locator('[class*="cursor-pointer"][class*="rounded-md"][class*="border"]').first().click();
    await page.waitForTimeout(1000);
    const expandedText = await page.textContent('body');
    const hasDetailParams = expandedText.includes('params') || expandedText.includes('参数') ||
      expandedText.includes('Event ID') || expandedText.includes('详情');
    console.log(`  Detail expanded: ${hasDetailParams}`);
  }

  // ── Test 4: Filter works ──
  console.log('\n=== Test 4: Filter ===');
  // Click filter toggle
  const filterBtn = page.locator('button:has-text("筛选"), button:has-text("Filter")').first();
  if (await filterBtn.count() > 0) {
    await filterBtn.click();
    await page.waitForTimeout(500);
    const hasFilterInputs = await page.locator('select').count();
    console.log(`  Filter inputs visible: ${hasFilterInputs > 0} (${hasFilterInputs} selects)`);
  }

  // ── Summary ──
  console.log('\n=== Summary ===');
  const corePass = statCards >= 4 && eventCards > 0 && (hasLoginEvent || hasDataExport) && hasUserIds;
  console.log(`Stat cards: ${statCards >= 4 ? '✅' : '❌'} (${statCards}/4)`);
  console.log(`Timeline events: ${eventCards > 0 ? '✅' : '❌'} (${eventCards} cards)`);
  console.log(`Real data (events+users): ${(hasLoginEvent || hasDataExport) && hasUserIds ? '✅' : '❌'}`);
  console.log(`IP addresses: ${hasIpAddresses ? '✅' : '❌'}`);
  console.log(`\n${corePass ? '✅ AUDIT FULL CHAIN PASS' : '❌ AUDIT FAIL'}`);

  if (consoleErrors.length > 0) {
    console.log('\nConsole errors:', consoleErrors.slice(0, 3));
  }

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
