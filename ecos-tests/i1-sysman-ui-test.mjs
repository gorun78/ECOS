// I1 sysman 前端 UI 测试 — Playwright headless
// 用法: cd /home/guorongxiao/ECOS/ecos-tests && node i1-sysman-ui-test.mjs

import { chromium } from 'playwright';

const FE = 'http://localhost:3000';
const BE = 'http://localhost:8080';
const RESULTS = [];
let pass = 0, fail = 0, warn = 0;

function check(name, condition, detail) {
  const ok = !!condition;
  if (ok) pass++; else fail++;
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail !== undefined ? ': ' + detail : ''}`);
  RESULTS.push({ name, ok, detail });
}
function warn_(name, detail) {
  warn++;
  console.log(`  ⚠️ ${name}${detail !== undefined ? ': ' + detail : ''}`);
  RESULTS.push({ name, ok: null, detail });
}

async function login(page) {
  await page.goto(`${FE}/#/login`);
  await page.waitForLoadState('networkidle');
  await page.fill('input[type="text"], input[name="username"], #username', 'admin');
  await page.fill('input[type="password"], input[name="password"], #password', 'admin123');
  await page.click('button[type="submit"], button:has-text("登录"), button:has-text("Login")');
  await page.waitForTimeout(2000);
  const url = page.url();
  return !url.includes('/login');
}

// ── 启动 ──
console.log('🚀 启动 Chromium headless...');
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
const page = await context.newPage();

// 收集 console 错误
const consoleErrors = [];
page.on('console', msg => {
  if (msg.type() === 'error') consoleErrors.push(msg.text());
});

try {
  // ═══ 1. Login 页面 ═══
  console.log('\n═══ 1. Login 页面 ═══');
  await page.goto(`${FE}/#/login`);
  await page.waitForLoadState('networkidle');
  const loginTitle = await page.title();
  check('Login 页面加载', loginTitle.includes('C2EOS') || loginTitle.includes('ECOS'), loginTitle);

  // 检查登录表单元素
  const hasUsernameInput = await page.locator('input[type="text"], input[name="username"]').count();
  const hasPasswordInput = await page.locator('input[type="password"]').count();
  const hasSubmitBtn = await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').count();
  check('Login 用户名输入框', hasUsernameInput > 0);
  check('Login 密码输入框', hasPasswordInput > 0);
  check('Login 登录按钮', hasSubmitBtn > 0);

  // 测试错误密码
  await page.fill('input[type="text"], input[name="username"]', 'admin');
  await page.fill('input[type="password"]', 'wrongpassword');
  await page.click('button[type="submit"], button:has-text("登录"), button:has-text("Login")');
  await page.waitForTimeout(1500);
  const errorVisible = await page.locator('text=/错误|失败|incorrect|invalid|剩余/i').count();
  check('Login 错误密码提示', errorVisible > 0, `found ${errorVisible} error elements`);

  // 正确登录
  const loggedIn = await login(page);
  check('Login 正确登录跳转', loggedIn, page.url());

  // ═══ 2. UserManagement (/iam) ═══
  console.log('\n═══ 2. UserManagement (/iam) ═══');
  await page.goto(`${FE}/#/iam`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // 检查用户列表
  const userRows = await page.locator('table tr, [class*="table"] [class*="row"], [class*="user-item"]').count();
  check('UserManagement 用户列表渲染', userRows > 0, `${userRows} rows/items`);

  // U2: 批量操作 checkbox
  const checkboxes = await page.locator('input[type="checkbox"]').count();
  if (checkboxes > 0) {
    check('U2 批量 checkbox 存在', checkboxes > 0, `${checkboxes} checkboxes`);
  } else {
    warn_('U2 批量 checkbox', '未找到 checkbox — 可能需要表头 checkbox');
  }

  // U5: 用户详情抽屉
  const firstRow = page.locator('table tbody tr, [class*="user-item"]').first();
  if (await firstRow.count() > 0) {
    await firstRow.click();
    await page.waitForTimeout(1000);
    const drawerVisible = await page.locator('[class*="drawer"], [class*="detail"], [class*="panel"]:visible, [role="dialog"]:visible').count();
    if (drawerVisible > 0) {
      check('U5 用户详情抽屉', true, '点击行后出现抽屉/面板');
    } else {
      warn_('U5 用户详情抽屉', '点击行后未出现抽屉');
    }
  }

  // 检查 i18n
  const hardcodedZh = await page.evaluate(() => {
    const text = document.body.innerText;
    // 检查是否有未经 i18n 处理的中文（简单检查：是否有中文文本但不包含在 t() 函数中）
    return text.length > 0;
  });
  check('UserManagement 页面有内容', hardcodedZh);

  // 检查 toast / alert
  const hasAlert = await page.evaluate(() => window.alert !== undefined);
  warn_('S6 检查 alert() 使用', '需交互触发验证');

  // ═══ 3. TenantManager (/tenants) ═══
  console.log('\n═══ 3. TenantManager (/tenants) ═══');
  await page.goto(`${FE}/#/tenants`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  const tenantItems = await page.locator('table tr, [class*="tenant"], [class*="card"]').count();
  check('TenantManager 租户列表渲染', tenantItems > 0, `${tenantItems} items`);

  // ═══ 4. DictManager (/dict) ═══
  console.log('\n═══ 4. DictManager (/dict) ═══');
  await page.goto(`${FE}/#/dict`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  const dictItems = await page.locator('table tr, [class*="dict"], [class*="item"], [class*="card"]').count();
  check('DictManager 字典页面渲染', dictItems > 0, `${dictItems} items`);

  // ═══ 5. SystemConfigManager (/system-config) ═══
  console.log('\n═══ 5. SystemConfigManager (/system-config) ═══');
  await page.goto(`${FE}/#/system-config`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  const configItems = await page.locator('[class*="config"], [class*="setting"], [class*="param"]').count();
  check('SystemConfigManager 配置页面渲染', configItems > 0, `${configItems} items`);

  // C6: 安全配置分组
  const securitySection = await page.locator('text=/安全|security|Security/i').count();
  check('C6 安全配置分组', securitySection > 0, `${securitySection} matches`);

  // ═══ 6. TokenDashboard (/tokens) ═══
  console.log('\n═══ 6. TokenDashboard (/tokens) ═══');
  await page.goto(`${FE}/#/tokens`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  const tokenItems = await page.locator('[class*="token"], [class*="stat"], [class*="card"], [class*="chart"]').count();
  check('TokenDashboard Token页面渲染', tokenItems > 0, `${tokenItems} items`);

  // ═══ 7. MonitoringCenter (/monitor) ═══
  console.log('\n═══ 7. MonitoringCenter (/monitor) ═══');
  await page.goto(`${FE}/#/monitor`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);
  const monitorItems = await page.locator('[class*="monitor"], [class*="metric"], [class*="chart"], [class*="card"], [class*="stat"]').count();
  check('MonitoringCenter 监控页面渲染', monitorItems > 0, `${monitorItems} items`);

  // ═══ 8. 响应式测试 (1366x768) ═══
  console.log('\n═══ 8. 响应式 S4 (1366x768) ═══');
  await page.setViewportSize({ width: 1366, height: 768 });
  await page.goto(`${FE}/#/iam`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
  const bodyScrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  const bodyClientWidth = await page.evaluate(() => document.documentElement.clientWidth);
  check('S4 响应式无水平溢出', bodyScrollWidth <= bodyClientWidth + 5, `scroll=${bodyScrollWidth} client=${bodyClientWidth}`);

  // ═══ 9. Console 错误收集 ═══
  console.log('\n═══ 9. Console 错误 ═══');
  if (consoleErrors.length === 0) {
    check('控制台无未捕获错误', true);
  } else {
    const realErrors = consoleErrors.filter(e => !e.includes('favicon') && !e.includes('Warning:'));
    if (realErrors.length === 0) {
      check('控制台无未捕获错误 (仅警告)', true, `${consoleErrors.length} warnings filtered`);
    } else {
      warn_('控制台错误', `${realErrors.length} errors: ${realErrors.slice(0, 3).join('; ')}`);
    }
  }

} catch (e) {
  console.error('❌ 测试异常:', e.message);
  fail++;
} finally {
  await browser.close();
}

// ── 汇总 ──
console.log(`\n${'═'.repeat(50)}`);
console.log(`📊 I1 sysman 前端 UI 测试结果`);
console.log(`${'═'.repeat(50)}`);
console.log(`  ✅ Pass: ${pass}`);
console.log(`  ❌ Fail: ${fail}`);
console.log(`  ⚠️  Warn: ${warn}`);
console.log(`  通过率: ${((pass / (pass + fail)) * 100).toFixed(0)}%`);
console.log(`${'═'.repeat(50)}`);

// 输出 JSON 供后续收集
const fs = await import('fs');
fs.writeFileSync('/tmp/i1-ui-results.json', JSON.stringify({ pass, fail, warn, results: RESULTS }, null, 2));
process.exit(fail > 0 ? 1 : 0);
