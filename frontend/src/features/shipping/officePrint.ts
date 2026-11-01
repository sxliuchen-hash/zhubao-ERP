/** 独立打开系统办公打印窗口，A5清单和40×30袋贴纸都不写出库。 */
export async function printOfficeDocument(title: string, markup: string, size: 'A5' | '40mm 30mm', css = '') {
  const page = window.open('', '_blank');
  if (!page) throw new Error('打印窗口未打开，请允许本页弹出打印窗口');
  page.opener = null;
  page.document.open();
  page.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>@page{size:${size};margin:${size === 'A5' ? '6mm' : '0'}}*{box-sizing:border-box;font-family:SimSun!important}body{margin:0}table{width:100%;border-collapse:collapse}td,th{border:1px solid #777;padding:1mm;overflow-wrap:anywhere}header{display:flex;justify-content:space-between;gap:3mm}.a5-sheet{width:auto!important;height:auto!important;min-height:190mm;break-after:page}h1{font-size:14pt}p,table{font-size:9pt}${css}</style></head><body>${markup}</body></html>`);
  page.document.title = title;
  page.document.close();
  if (page.document.fonts) await page.document.fonts.ready;
  page.focus(); page.print();
}
