import { pinyin } from 'pinyin-pro';


/** 客户暗记只允许页脚上的短字母，避免把中文名或任意文字印出去。 */
export const customerMarkPattern = /^[A-Z]{1,8}$/;

/**
 * 客户名称的汉字拼音首字母。英文单词只取首字母。
 * 供内部辨认，贴纸上不出现中文名。
 */
export function customerInitials(name: string): string {
  const text = name.trim();
  if (!text) return '';
  const tokens = pinyin(text, {
    pattern: 'first',
    toneType: 'none',
    type: 'array',
    nonZh: 'consecutive',
  });
  const letters = tokens.map((token) => {
    const latin = token.replace(/[^A-Za-z]/g, '');
    if (!latin) return '';
    return latin.length === 1 ? latin : latin[0];
  }).join('');
  return letters.toUpperCase().slice(0, 8);
}
