/** 本机草稿保存真实 Blob／File；数据库键包含账号和用途，不保存登录令牌。 */
const databaseName = 'zhubao-receipt-drafts';
const queues = new Map<string, Promise<unknown>>();
function key(account: string, scope: string): string {
  if (!account.trim()) throw new Error('请登录后保存本机草稿');
  return JSON.stringify([account.trim(), scope]);
}
function database(): Promise<IDBDatabase> {
  if (!globalThis.indexedDB) return Promise.reject(new Error('当前浏览器不支持本机文件草稿，刷新无法恢复文件'));
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, 1);
    request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('drafts')) request.result.createObjectStore('drafts'); };
    request.onsuccess = () => { request.result.onversionchange = () => request.result.close(); resolve(request.result); };
    request.onerror = () => reject(new Error('本机草稿数据库无法打开，请检查浏览器存储权限'));
    request.onblocked = () => reject(new Error('草稿数据库被其他页面占用，请关闭旧页面后重试'));
  });
}
async function transaction<T>(account: string, scope: string, action: 'get' | 'put' | 'delete', value?: T): Promise<T | null> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', action === 'get' ? 'readonly' : 'readwrite');
    const store = tx.objectStore('drafts');
    let request: IDBRequest;
    try { request = action === 'put' ? store.put(value, key(account, scope)) : action === 'delete' ? store.delete(key(account, scope)) : store.get(key(account, scope)); }
    catch { db.close(); reject(new Error('草稿文件或资料未能保存，请保留当前页面并清理已关联文件的本机副本后重试')); return; }
    let result: T | null = null;
    request.onsuccess = () => { result = action === 'get' ? (request.result ?? null) : (value ?? null); };
    tx.oncomplete = () => { db.close(); resolve(result); };
    tx.onerror = tx.onabort = () => { db.close(); reject(new Error('本机草稿保存失败，可能空间不足；请保留当前页面并清理已关联文件的本机副本后重试')); };
  });
}
export async function readIntakeDraft<T>(account: string, scope: string): Promise<T | null> { await queues.get(key(account, scope))?.catch(() => {}); return transaction<T>(account, scope, 'get'); }
export function writeIntakeDraft<T>(account: string, scope: string, value: T): Promise<void> {
  const id = key(account, scope);
  const operation = (queues.get(id) ?? Promise.resolve()).catch(() => {}).then(() => transaction(account, scope, 'put', value)).then(() => {});
  queues.set(id, operation);
  return operation;
}
export async function deleteIntakeDraft(account: string, scope: string): Promise<void> { await queues.get(key(account, scope))?.catch(() => {}); await transaction(account, scope, 'delete'); }
