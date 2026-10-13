/**
 * 2026-10-14 从 frontend/src/features/scan/progressTarget.ts 移出 progressTargetOf。
 * 生产、质检、发货面板使用同文件的 progressTargetFromScan，本函数没有任何调用方。
 * 恢复时把函数接回现行 progressTarget.ts，不要用本文件替换整个模块。
 */

export interface ProgressTarget {
  targetType: 'MOTHER_ORDER' | 'WORK_ORDER';
  targetCode: string;
  motherOrderCode: string;
}

export function progressTargetOf(
  context?: { motherOrderCode?: string | null; workOrderCode?: string | null } | null,
): ProgressTarget | null {
  const mother = context?.motherOrderCode?.trim() ?? '';
  const work = context?.workOrderCode?.trim() ?? '';
  if (work && work !== mother) {
    return { targetType: 'WORK_ORDER', targetCode: work, motherOrderCode: mother };
  }
  if (mother) {
    return { targetType: 'MOTHER_ORDER', targetCode: mother, motherOrderCode: mother };
  }
  return null;
}
