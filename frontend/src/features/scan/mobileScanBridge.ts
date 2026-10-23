type OpenScan = () => void;

let target: OpenScan | null = null;
let pending = false;

/** 当前页的扫码框登记自己。若底栏已经点过，挂上后立刻打开。 */
export function bindMobileScanTarget(open: OpenScan) {
  target = open;
  if (!pending) return;
  pending = false;
  open();
}

export function unbindMobileScanTarget(open: OpenScan) {
  if (target === open) target = null;
}

/** 当前页能扫就直接打开，并返回是否已经打开。 */
export function openMobileScanTarget(): boolean {
  if (!target) return false;
  target();
  return true;
}

/** 先记住这次点击，等现场作业的扫码框挂上后再打开。 */
export function armMobileScan() {
  pending = true;
}

export function cancelMobileScan() {
  pending = false;
}
