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
  await page.screenshot({ path: path.join(outputDir, 'm356-dashboard.png'), fullPage: false });

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
      hasReceiptFields: text(document.body).includes('拆箱') &&
        text(document.body).includes('寄货人') &&
        text(document.body).includes('收货人'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm356-inbound.png'), fullPage: false });

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
      hasSiteRegisterFields: text(document.body).includes('雕刻') &&
        text(document.body).includes('ENG-03') &&
        text(document.body).includes('交单审核'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm356-production.png'), fullPage: false });

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
  await page.screenshot({ path: path.join(outputDir, 'm356-quality.png'), fullPage: false });

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
  await page.screenshot({ path: path.join(outputDir, 'm356-inventory.png'), fullPage: false });

  await page.click('button[data-tab="configuration"]');
  await page.waitForTimeout(300);

  const configuration = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();
    const bodyText = text(document.body);

    return {
      title: text(document.querySelector('h1')),
      hasSecurityPanel: bodyText.includes('当前权限 / 数据范围边界'),
      hasHeaders: bodyText.includes('X-Zhubao-Role') && bodyText.includes('X-Zhubao-Data-Scope'),
      hasSiteProcessTemplates: bodyText.includes('PROC-ENGRAVE-DAY') &&
        bodyText.includes('PROC-POLISH-DAILY'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm356-configuration.png'), fullPage: false });

  await page.click('button[data-tab="operations"]');
  await page.waitForTimeout(300);

  const operations = await page.evaluate(() => {
    const text = (el) => (el?.textContent || '').replace(/\s+/g, ' ').trim();
    const bodyText = text(document.body);

    return {
      title: text(document.querySelector('h1')),
      hasCosService: bodyText.includes('腾讯云 COS 备份'),
      hasAlerts: bodyText.includes('告警汇总') && bodyText.includes('OPS-ALERT'),
      hasCosBackupStatus: bodyText.includes('COS-BACKUP-DISABLED') || bodyText.includes('COS-BACKUP-READY'),
      hasCosChecklist: bodyText.includes('OPS-CHK-007'),
      hasRestoreDrill: bodyText.includes('恢复演练校验') && bodyText.includes('RESTORE-DRILL'),
      hasStructuredRestoreDrill: bodyText.includes('结构化恢复演练记录') && bodyText.includes('backup_checksum'),
      hasRestoreChecklist: bodyText.includes('OPS-CHK-008'),
      hasM362Label: bodyText.includes('M3.62 运维告警汇总'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  await page.screenshot({ path: path.join(outputDir, 'm356-operations.png'), fullPage: false });

  await browser.close();

  const result = {
    dashboard,
    inbound,
    production,
    quality,
    inventory,
    configuration,
    operations,
    console: messages.filter((message) => ['error', 'warning'].includes(message.type)),
  };

  const failures = [];
  for (const [name, section] of Object.entries({ dashboard, inbound, production, quality, inventory, configuration, operations })) {
    if (section.overflow) {
      failures.push(`${name} has horizontal overflow: ${section.scrollWidth} > ${section.clientWidth}`);
    }
  }
  if (inbound.selectedRows !== 1 || inbound.activeCode !== 'MD-20260706-003') {
    failures.push(`inbound selection mismatch: ${JSON.stringify(inbound)}`);
  }
  if (!dashboard.pageEyebrow.includes('M3.56') || !dashboard.baseline.includes('M3.56')) {
    failures.push(`dashboard M3.56 baseline missing: ${JSON.stringify(dashboard)}`);
  }
  if (!inbound.hasReceiptFields) {
    failures.push(`inbound receipt fields missing: ${JSON.stringify(inbound)}`);
  }
  if (production.selectedRows !== 1 || production.activeCode !== 'ZD-20260706-001') {
    failures.push(`production selection mismatch: ${JSON.stringify(production)}`);
  }
  if (!production.hasSiteRegisterFields) {
    failures.push(`production site register fields missing: ${JSON.stringify(production)}`);
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
  if (!configuration.hasSiteProcessTemplates) {
    failures.push(`configuration site process templates missing: ${JSON.stringify(configuration)}`);
  }
  if (!operations.hasCosService || !operations.hasAlerts || !operations.hasCosBackupStatus || !operations.hasCosChecklist || !operations.hasRestoreDrill || !operations.hasStructuredRestoreDrill || !operations.hasRestoreChecklist || !operations.hasM362Label) {
    failures.push(`operations backup/restore readiness missing: ${JSON.stringify(operations)}`);
  }
  if (result.console.length > 0) {
    failures.push(`console warnings/errors: ${JSON.stringify(result.console)}`);
  }

  fs.writeFileSync(path.join(outputDir, 'm356-ui-check.json'), `${JSON.stringify(result, null, 2)}\n`);
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
