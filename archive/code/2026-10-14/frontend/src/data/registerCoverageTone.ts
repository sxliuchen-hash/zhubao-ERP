/**
 * 2026-10-14 从 frontend/src/data/registerImportTemplates.ts 移出。
 * 没有任何页面或测试调用。字段覆盖色调仍由同文件内部的 fieldCoverageTone 计算。
 */

export function registerCoverageTone(status: RegisterFieldCoverage['status']): Tone {
  return fieldCoverageTone(status);
}
