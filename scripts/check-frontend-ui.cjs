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
  await page.screenshot({ path: path.join(outputDir, 'm353-dashboard.png'), fullPage: false });

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
  await page.screenshot({ path: path.join(outputDir, 'm353-inbound.png'), fullPage: false });

  await page.click('button[data-tab="production"]');
  await page.waitForTimeout(300);
  await page.click("table.production-dispatch-table tbody tr:has-text('ZD-20260706-001')");
  await page.waitForTimeout(200);

  const production = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();

    return {
      title: text(document.querySelector('h1')),
      selectedRows: document.querySelectorAll('table.production-dispatch-table tbody tr.is-selected').length,
      activeCode: text(document.querySelector('.active-object-card > strong')),
      actionTrace: Array.from(document.querySelectorAll('.operation-action-trace strong')).map(text),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm353-production.png'), fullPage: false });

  await page.click('button[data-tab="quality"]');
  await page.waitForTimeout(300);
  await page.click("table.quality-dispatch-table tbody tr:has-text('QC-20260706-022')");
  await page.waitForTimeout(200);

  const quality = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();

    return {
      title: text(document.querySelector('h1')),
      selectedRows: document.querySelectorAll('table.quality-dispatch-table tbody tr.is-selected').length,
      activeCode: text(document.querySelector('.active-object-card > strong')),
      actionTrace: Array.from(document.querySelectorAll('.operation-action-trace strong')).map(text),
      hasLegacyRuntimeBoard: Boolean(document.querySelector('.quality-runtime-board')),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm353-quality.png'), fullPage: false });

  await page.click('button[data-tab="inventory"]');
  await page.waitForTimeout(300);
  await page.click("table.inventory-dispatch-table tbody tr:has-text('ZD-20260706-001')");
  await page.waitForTimeout(200);

  const inventory = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();

    return {
      title: text(document.querySelector('h1')),
      selectedRows: document.querySelectorAll('table.inventory-dispatch-table tbody tr.is-selected').length,
      activeCode: text(document.querySelector('.active-object-card > strong')),
      actionTrace: Array.from(document.querySelectorAll('.operation-action-trace strong')).map(text),
      hasLedgerTitle: text(document.body).includes('库存台账明细'),
      hasTraceEvidenceTitle: text(document.body).includes('追溯证据'),
      hasLegacyOpsBoard: Boolean(document.querySelector('.inventory-trace-desk > .module-ops-board')),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm353-inventory.png'), fullPage: false });

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
  await page.screenshot({ path: path.join(outputDir, 'm353-configuration.png'), fullPage: false });

  await browser.close();

  const result = {
    dashboard,
    inbound,
    production,
    quality,
    inventory,
    configuration,
    console: messages.filter((message) => ['error', 'warning'].includes(message.type)),
  };

  const failures = [];
  for (const [name, section] of Object.entries({ dashboard, inbound, production, quality, inventory, configuration })) {
    if (section.overflow) {
      failures.push(`${name} has horizontal overflow: ${section.scrollWidth} > ${section.clientWidth}`);
    }
  }
  if (inbound.selectedRows !== 1 || inbound.activeCode !== 'MD-20260706-003') {
    failures.push(`inbound selection mismatch: ${JSON.stringify(inbound)}`);
  }
  if (production.selectedRows !== 1 || production.activeCode !== 'ZD-20260706-001') {
    failures.push(`production selection mismatch: ${JSON.stringify(production)}`);
  }
  if (!production.actionTrace.includes('扫码进站') || !production.actionTrace.includes('送检入账')) {
    failures.push(`production action trace incomplete: ${production.actionTrace.join(', ')}`);
  }
  if (quality.selectedRows !== 1 || quality.activeCode !== 'QC-20260706-022') {
    failures.push(`quality selection mismatch: ${JSON.stringify(quality)}`);
  }
  if (!quality.actionTrace.includes('冻结异常库存') || !quality.actionTrace.includes('损耗审批')) {
    failures.push(`quality action trace incomplete: ${quality.actionTrace.join(', ')}`);
  }
  if (quality.hasLegacyRuntimeBoard) {
    failures.push('quality legacy runtime board is still rendered');
  }
  if (inventory.selectedRows !== 1 || inventory.activeCode !== 'ZD-20260706-001') {
    failures.push(`inventory selection mismatch: ${JSON.stringify(inventory)}`);
  }
  if (!inventory.actionTrace.includes('客户权属') || !inventory.actionTrace.includes('售后复核')) {
    failures.push(`inventory action trace incomplete: ${inventory.actionTrace.join(', ')}`);
  }
  if (!inventory.hasLedgerTitle || !inventory.hasTraceEvidenceTitle) {
    failures.push(`inventory missing trace anchors: ${JSON.stringify(inventory)}`);
  }
  if (inventory.hasLegacyOpsBoard) {
    failures.push('inventory legacy operations board is still rendered');
  }
  if (!configuration.hasSecurityPanel || !configuration.hasHeaders) {
    failures.push(`configuration security context missing: ${JSON.stringify(configuration)}`);
  }
  if (result.console.length > 0) {
    failures.push(`console warnings/errors: ${JSON.stringify(result.console)}`);
  }

  fs.writeFileSync(path.join(outputDir, 'm353-ui-check.json'), `${JSON.stringify(result, null, 2)}\n`);
  if (failures.length > 0) {
    console.error(JSON.stringify({ result, failures }, null, 2));
    process.exit(1);
  }
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
