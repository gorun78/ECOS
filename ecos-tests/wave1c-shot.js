const { chromium } = require('playwright');
const path = '/home/guorongxiao/.cache/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-linux64/chrome-headless-shell';
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: path });
  const page = await (await browser.newContext()).newPage();
  await page.goto('http://localhost:3000/#/mission_control', { waitUntil: 'domcontentloaded' });
  await page.evaluate(() => {
    localStorage.setItem('ecos_token', 'w1c');
  }).catch(() => {});
  await page.waitForTimeout(2500);
  const data = await page.evaluate(() => {
    const r = document.getElementById('root');
    const txt = body => (body?.innerText || '').split('\n').map(s => s.trim()).filter(Boolean).slice(0, 25);
    return {
      bodyText: txt(document.body).slice(0, 50),
      rootChild: r?.childElementCount,
      h1: document.querySelector('h1, [class*=title]')?.textContent?.slice(0, 30),
    };
  });
  console.log(JSON.stringify(data, null, 2));
  await page.screenshot({ path: '/tmp/w1c-shot.png', fullPage: false });
  console.log('screenshot saved /tmp/w1c-shot.png');
  await browser.close();
})();
