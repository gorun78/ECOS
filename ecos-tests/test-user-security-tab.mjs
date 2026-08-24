// Test: UserEditModal security tab shows 4 config fields
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

try {
  // Login
  await page.goto('http://localhost:3000/#/login');
  await page.waitForLoadState('networkidle');
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // Go to IAM → users tab
  await page.goto('http://localhost:3000/#/iam');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click edit button (index 1) on first user row
  const editBtn = page.locator('table tbody tr').first().locator('button').nth(1);
  await editBtn.click();
  await page.waitForTimeout(1000);

  // Click "安全配置" tab
  const secTab = page.locator('.fixed.inset-0').last().locator('button:has-text("安全配置")');
  await secTab.click();
  await page.waitForTimeout(3000); // Wait for security profile to load

  // Check the security tab content
  const modal = page.locator('.fixed.inset-0').last();
  const modalText = await modal.textContent();

  console.log('=== Security Tab Analysis ===');
  console.log('Has "安全准入等级":', modalText.includes('安全准入等级'));
  console.log('Has "绑定物理工作站":', modalText.includes('绑定物理工作站'));
  console.log('Has "双写审计力度":', modalText.includes('双写审计力度'));
  console.log('Has "沙盒审查":', modalText.includes('沙盒审查'));
  console.log('Has "保存安全配置":', modalText.includes('保存安全配置'));
  console.log('Has "账户状态":', modalText.includes('账户状态'));

  const selects = await modal.locator('select').count();
  const checkboxes = await modal.locator('input[type="checkbox"]').count();
  const textInputs = await modal.locator('input[type="text"]').count();
  console.log('Select elements:', selects, '(expect >=2: clearance+audit)');
  console.log('Checkbox elements:', checkboxes, '(expect >=1: sandbox)');
  console.log('Text input elements:', textInputs, '(expect >=1: workstation)');

  const saveBtn = await modal.locator('button:has-text("保存安全配置")').count();
  console.log('Save security button:', saveBtn);

  const allPresent = modalText.includes('安全准入等级') &&
    modalText.includes('绑定物理工作站') &&
    modalText.includes('双写审计力度') &&
    modalText.includes('沙盒审查') &&
    selects >= 2 && checkboxes >= 1 && textInputs >= 1;

  console.log('\n' + (allPresent ? '✅ PASS — All 4 security config fields present' : '❌ FAIL — Missing fields'));

  await page.screenshot({ path: '/tmp/user-security-tab.png' });

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
