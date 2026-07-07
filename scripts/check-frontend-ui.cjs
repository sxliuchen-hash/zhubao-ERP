const fs = require('node:fs');
const path = require('node:path');

function loadPlaywright() {
  try {
    return require('playwright');
  } catch {
    const npxRoot = path.resolve('.npm-cache/_npx');
    if (fs.existsSync(npxRoot)) {
      for (const entry of fs.readdirSync(npxRoot)) {
        const candidate = path.join(npxRoot, entry, 'node_modules', 'playwright');
        if (fs.existsSync(candidate)) {
          return require(candidate);
        }
      }
    }
    throw new Error(
      '缺少 Playwright 包。请先运行：$env:npm_config_cache=".\\.npm-cache"; npx --yes --package playwright playwright --version'
    );
  }
}

const { chromium } = loadPlaywright();

const targetUrl = process.argv[2] ?? 'http://localhost:5173/';
const outputDir = path.resolve('output/playwright');

fs.mkdirSync(outputDir, { recursive: true });

async function inspectLayout(page) {
  return page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();
    const header = document.querySelector('.ops-page-header');

    return {
      title: text(document.querySelector('h1')),
      baseline: text(document.querySelector('.ops-status-card')).slice(0, 100),
      pageEyebrow: text(document.querySelector('.ops-page-header .eyebrow')),
      innerEyebrow: text(document.querySelector('.mission-copy .eyebrow')),
      headerHeight: Math.round(header?.getBoundingClientRect().height || 0),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
}

async function main() {
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
  } catch (error) {
    try {
      browser = await chromium.launch({ channel: 'msedge', headless: true });
    } catch {
      browser = await chromium.launch({ channel: 'chrome', headless: true });
    }
  }
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  const messages = [];

  page.on('console', (message) => {
    messages.push({ type: message.type(), text: message.text() });
  });

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 15_000 });
  await page.waitForTimeout(500);

  const dashboard = await inspectLayout(page);
  await page.screenshot({ path: path.join(outputDir, 'm352-dashboard.png'), fullPage: false });

  await page.click('button[data-tab="inbound"]');
  await page.waitForTimeout(300);
  await page.click("tbody tr:has-text('MD-20260706-003')");
  await page.waitForTimeout(200);

  const inbound = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();

    return {
      title: text(document.querySelector('h1')),
      selectedRows: document.querySelectorAll('tbody tr.is-selected').length,
      activeCode: text(document.querySelector('.active-object-card > strong')),
      actionTrace: Array.from(document.querySelectorAll('.inbound-action-trace strong')).map(text),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm352-inbound.png'), fullPage: false });

  await page.click('button[data-tab="configuration"]');
  await page.waitForTimeout(300);

  const configuration = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();
    const bodyText = text(document.body);

    return {
      title: text(document.querySelector('h1')),
      hasSecurityPanel: bodyText.includes('当前权限 / 数据范围边界'),
      hasHeaders: bodyText.includes('X-Zhubao-Role') && bodyText.includes('X-Zhubao-Data-Scope'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm352-configuration.png'), fullPage: false });

  await browser.close();

  const result = {
    dashboard,
    inbound,
    configuration,
    console: messages.filter((message) => ['error', 'warning'].includes(message.type)),
  };

  fs.writeFileSync(path.join(outputDir, 'm352-ui-check.json'), `${JSON.stringify(result, null, 2)}\n`);
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
