import type { ScanObjectType } from '../../services/scanContextApi';

/** 相机、照片、扫码枪和手输交给查询前的原始内容。类型只在用户明确选择时出现。 */
export interface ScanDraft {
  content: string;
  objectType?: ScanObjectType;
}
