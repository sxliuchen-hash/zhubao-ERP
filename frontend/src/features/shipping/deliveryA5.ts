/** A5 发货清单的一袋。重量未知保持空，不写成 0。 */
export interface A5BagLine {
  lineNo: number;
  bagCode: string;
  material: string;
  spec: string;
  quantity: string;
  unit: string;
  weight: string | null;
  requirement: string;
  sourceCode: string;
}

export interface A5UnitTotal {
  unit: string;
  quantity: string;
}

export interface A5Page {
  deliveryId: string;
  customerName: string;
  logistics: string;
  pageNo: number;
  pageCount: number;
  rows: A5BagLine[];
  pageTotals: A5UnitTotal[];
  grandTotals: A5UnitTotal[] | null;
  feeText: string;
}

const ROWS_PER_PAGE = 8;

/** 用已确认交付快照分页。费用未定显示未定价，不补 0。 */
export function buildA5Pages(input: {
  deliveryId: string;
  customerName: string;
  logistics: string;
  lines: A5BagLine[];
  feeAmount: string | null;
}): A5Page[] {
  const lines = [...input.lines].sort((left, right) => left.lineNo - right.lineNo);
  const pageCount = Math.max(1, Math.ceil(lines.length / ROWS_PER_PAGE));
  const grand = totals(lines);
  const feeText = input.feeAmount == null || input.feeAmount.trim() === '' ? '未定价' : `${input.feeAmount}元`;
  const pages: A5Page[] = [];
  for (let page = 0; page < pageCount; page += 1) {
    const rows = lines.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE);
    pages.push({
      deliveryId: input.deliveryId,
      customerName: input.customerName,
      logistics: input.logistics,
      pageNo: page + 1,
      pageCount,
      rows,
      pageTotals: totals(rows),
      grandTotals: page + 1 === pageCount ? grand : null,
      feeText,
    });
  }
  return pages;
}

export function renderA5Markup(page: A5Page): string {
  const rows = page.rows.map(row => `<tr><td>${row.lineNo}</td><td>${escapeHtml(row.bagCode)}</td><td>${escapeHtml(row.material)}</td><td>${escapeHtml(row.spec)}</td><td>${escapeHtml(row.quantity)}${escapeHtml(row.unit)}</td><td>${row.weight == null ? '未称' : `${escapeHtml(row.weight)}克`}</td><td>${escapeHtml(row.requirement)}</td><td>${escapeHtml(row.sourceCode)}</td></tr>`).join('');
  const pageTotals = page.pageTotals.map(item => `${escapeHtml(item.quantity)}${escapeHtml(item.unit)}`).join('，');
  const grand = page.grandTotals == null ? '' : `<p>整单合计 ${page.grandTotals.map(item => `${escapeHtml(item.quantity)}${escapeHtml(item.unit)}`).join('，')}</p>`;
  return `<article class="a5-sheet" style="width:148mm;height:210mm"><header><span>${escapeHtml(page.deliveryId)}</span><span>${escapeHtml(page.customerName)}</span><span>${page.pageNo}/${page.pageCount}</span></header><p>物流 ${escapeHtml(page.logistics)}</p><table>${rows}</table><p>本页小计 ${pageTotals}</p>${grand}<p>费用 ${escapeHtml(page.feeText)}</p><p>经办____ 复核____ 客户签收____</p></article>`;
}

function totals(rows: A5BagLine[]): A5UnitTotal[] {
  const sums = new Map<string, number>();
  for (const row of rows) {
    sums.set(row.unit, (sums.get(row.unit) ?? 0) + Number(row.quantity));
  }
  return [...sums.entries()].map(([unit, quantity]) => ({ unit, quantity: String(quantity) }));
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
