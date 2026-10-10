const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

// 仅复用已安装依赖或已有缓存，不自动安装包和浏览器。
function loadPlaywright() {
  const candidates = [process.env.ZHUBAO_PLAYWRIGHT_MODULE, 'playwright', 'playwright-core'].filter(Boolean);
  const cacheRoots = [
    path.resolve('.npm-cache/_npx'),
    process.env.npm_config_cache && path.join(process.env.npm_config_cache, '_npx'),
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'npm-cache', '_npx'),
    path.join(os.homedir(), '.npm', '_npx'),
  ].filter(Boolean);
  for (const root of cacheRoots) {
    if (!fs.existsSync(root)) continue;
    for (const entry of fs.readdirSync(root)) {
      for (const name of ['playwright', 'playwright-core']) {
        candidates.push(path.join(root, entry, 'node_modules', name));
      }
    }
  }
  for (const candidate of candidates) {
    try { return require(candidate); } catch { /* 继续查找下一个已有缓存。 */ }
  }
  throw new Error('未找到已有 Playwright。请通过 ZHUBAO_PLAYWRIGHT_MODULE 指向现有包目录；本脚本不会安装依赖。');
}

const targetUrl = process.argv[2] ?? 'http://localhost:5173/';
const outputDir = path.resolve(process.env.ZHUBAO_UI_OUTPUT_DIR ?? 'output/playwright');
const viewports = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
];
const scenarios = [
  { key: 'dashboard', marker: '.command-center-v3' },
  { key: 'inbound', marker: '.inbound-desk', table: '.inbound-dispatch-table' },
  { key: 'production', marker: '.production-desk', table: '.production-dispatch-table' },
  { key: 'quality', marker: '.quality-desk', table: '.quality-dispatch-table' },
  { key: 'inventory', marker: '.inventory-trace-desk', table: '.inventory-dispatch-table' },
  { key: 'finance', marker: '.finance-workspace' },
  { key: 'configuration', marker: '[aria-label="用户管理"]' },
  { key: 'documents', marker: '.document-page' },
  { key: 'hardware', marker: '.hardware-desk' },
  { key: 'operations', marker: '.operations-center-page' },
];

function redact(value) {
  let text = String(value);
  for (const secret of [process.env.ZHUBAO_UI_PASSWORD, process.env.ZHUBAO_UI_TOKEN].filter(Boolean)) {
    text = text.split(secret).join('[已隐藏]');
  }
  return text;
}

async function settle(page) {
  await page.waitForLoadState('networkidle', { timeout: 15_000 });
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function enterWorkspace(page) {
  await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 20_000 });
  await page.waitForSelector('.ops-shell, .login-card', { timeout: 20_000 });
  if (await page.locator('form[aria-label="首次修改密码"]').isVisible()) {
    throw new Error('测试账号要求首次修改密码，请先在授权流程中完成改密，再运行只读页面检查。');
  }
  if (await page.locator('form[aria-label="登录"]').isVisible()) {
    const username = process.env.ZHUBAO_UI_USERNAME;
    const password = process.env.ZHUBAO_UI_PASSWORD;
    if (!username || !password) {
      throw new Error('当前没有有效会话。请提供 ZHUBAO_UI_STORAGE_STATE、ZHUBAO_UI_TOKEN，或 ZHUBAO_UI_USERNAME 与 ZHUBAO_UI_PASSWORD。');
    }
    const form = page.locator('form[aria-label="登录"]');
    await form.locator('input[autocomplete="username"]').fill(username);
    // 先触发账号失焦，再填写密码，避免演示账号自动填充覆盖测试密码。
    await form.locator('input[autocomplete="username"]').press('Tab');
    await settle(page);
    await form.locator('input[autocomplete="current-password"]').fill(password);
    await form.getByRole('button', { name: '登录', exact: true }).click();
    await page.waitForSelector('.ops-shell, form[aria-label="首次修改密码"], .login-message', { timeout: 20_000 });
    if (!(await page.locator('.ops-shell').isVisible())) {
      const hint = await page.locator('.login-message').textContent().catch(() => '');
      throw new Error(`测试账号登录未进入工作区；${hint || '请检查账号状态或首次改密要求。'}`);
    }
  }
  await settle(page);
}

async function openTab(page, key) {
  const desktop = page.locator(`button[data-tab="${key}"]`);
  if (await desktop.isVisible()) {
    await desktop.click();
    return;
  }
  let mobile = page.locator(`button[data-mobile-tab="${key}"]`);
  if (!(await mobile.isVisible())) {
    const more = page.locator('.mobile-tabbar > button[aria-haspopup="dialog"]');
    if (await more.isVisible()) await more.click();
    mobile = page.locator(`button[data-mobile-tab="${key}"]`);
  }
  if (!(await mobile.isVisible())) throw new Error(`当前账号没有可访问的 ${key} 菜单，请使用具有检查范围权限的测试账号。`);
  await mobile.click();
}

async function inspectLayout(page, marker) {
  return page.evaluate((selector) => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();
    const content = document.querySelector(selector);
    const width = document.documentElement.clientWidth;
    return {
      title: text(document.querySelector('.ops-page-header h1')),
      headings: Array.from(content?.querySelectorAll('h2, h3') ?? []).map(text),
      rows: content?.querySelectorAll('tbody tr').length ?? 0,
      emptyHints: Array.from(content?.querySelectorAll('.empty-hint') ?? []).map(text),
      visibleErrors: Array.from(content?.querySelectorAll('.operation-feedback.danger, [role="alert"]') ?? []).map(text),
      overflow: document.documentElement.scrollWidth > width + 1,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: width,
      overflowElements: Array.from(content?.querySelectorAll('*') ?? [])
        .filter((el) => {
          const bounds = el.getBoundingClientRect();
          return bounds.width > 0 && (bounds.right > width + 1 || bounds.left < -1)
            && getComputedStyle(el).position !== 'fixed'
            && !el.closest('.module-table-wrap, .document-table-wrap, .ops-table-wrap, .finance-table-wrap');
        })
        .slice(0, 8)
        .map((el) => ({ tag: el.tagName, className: String(el.className), text: text(el).slice(0, 80) })),
    };
  }, marker);
}

async function main() {
  const { chromium } = loadPlaywright();
  fs.mkdirSync(outputDir, { recursive: true });
  const result = { target: targetUrl, pages: [], console: [], pageErrors: [], requestFailures: [], blockedWrites: [], failures: [] };
  let browser;
  for (const options of [{}, { channel: 'msedge' }, { channel: 'chrome' }]) {
    try { browser = await chromium.launch({ ...options, headless: true }); break; } catch { /* 继续尝试已有浏览器。 */ }
  }
  if (!browser) throw new Error('无法启动已安装的 Chromium、Edge 或 Chrome；本脚本不会下载浏览器。');
  let activeScenario = '登录';
  let context;
  try {
    context = await browser.newContext({
      viewport: { width: viewports[0].width, height: viewports[0].height },
      ...(process.env.ZHUBAO_UI_STORAGE_STATE ? { storageState: process.env.ZHUBAO_UI_STORAGE_STATE } : {}),
    });
    if (process.env.ZHUBAO_UI_TOKEN) {
      await context.addInitScript((token) => localStorage.setItem('zhubao-auth-token', token), process.env.ZHUBAO_UI_TOKEN);
    }
    const page = await context.newPage();
    page.on('console', (message) => {
      if (['error', 'warning'].includes(message.type())) result.console.push({ page: activeScenario, type: message.type(), text: redact(message.text()) });
    });
    page.on('pageerror', (error) => result.pageErrors.push({ page: activeScenario, text: redact(error.message) }));
    page.on('requestfailed', (request) => result.requestFailures.push({ page: activeScenario, method: request.method(), url: new URL(request.url()).pathname, error: request.failure()?.errorText }));
    // 页面检查只允许查询和显式登录，防止误点或初始化逻辑写入业务。
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const pathname = new URL(request.url()).pathname;
      if (['GET', 'HEAD', 'OPTIONS'].includes(request.method()) || (request.method() === 'POST' && pathname === '/api/auth/login')) {
        await route.continue();
      } else {
        result.blockedWrites.push({ page: activeScenario, method: request.method(), path: pathname });
        await route.abort('blockedbyclient');
      }
    });
    await enterWorkspace(page);
    for (const viewport of viewports) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      for (const scenario of scenarios) {
        activeScenario = `${viewport.name}/${scenario.key}`;
        try {
          await openTab(page, scenario.key);
          await page.waitForSelector(scenario.marker, { timeout: 15_000 });
          await settle(page);
          let selection = { status: '无单据或无需选择' };
          if (scenario.table) {
            const rows = page.locator(`${scenario.table} tbody tr`)
              .filter({ has: page.locator('td'), hasNot: page.locator('td[colspan]') });
            if (await rows.count() && await rows.first().isVisible()) {
              await rows.first().click();
              await settle(page);
              selection = { status: '已选择实际第一行' };
            }
          }
          const layout = await inspectLayout(page, scenario.marker);
          const screenshot = `${viewport.name}-${scenario.key}.png`;
          await page.screenshot({ path: path.join(outputDir, screenshot), fullPage: true });
          result.pages.push({ viewport: viewport.name, key: scenario.key, screenshot, selection, ...layout });
          if (!layout.title) result.failures.push(`${activeScenario} 缺少页面标题`);
          if (layout.overflow) result.failures.push(`${activeScenario} 横向溢出：${layout.scrollWidth} > ${layout.clientWidth}`);
        } catch (error) {
          result.failures.push(`${activeScenario}：${redact(error.message)}`);
        }
      }
    }
  } catch (error) {
    result.failures.push(redact(error.message));
  } finally {
    await context?.close();
    await browser.close();
  }
  for (const key of ['console', 'pageErrors', 'requestFailures', 'blockedWrites']) {
    if (result[key].length) result.failures.push(`${key} 发现 ${result[key].length} 项异常，详见报告`);
  }
  fs.writeFileSync(path.join(outputDir, 'ui-check.json'), `${JSON.stringify(result, null, 2)}\n`);
  console.log(JSON.stringify(result, null, 2));
  if (result.failures.length) process.exitCode = 1;
}

main().catch((error) => {
  console.error(redact(error.message));
  process.exitCode = 1;
});
