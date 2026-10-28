/**
 * 安卓 10 上不少手机的系统 WebView 停在 Chrome 80–91。
 * 这些运行时方法缺失时，登录后的第一帧渲染会抛错，Vue 把根节点换成空注释，看起来就是一直白屏。
 */
export function installLegacyWebViewPolyfills(): void {
  installArrayAt();
  installStringReplaceAll();
  installRandomUuid();
}

function installArrayAt(): void {
  const proto = Array.prototype as unknown as { at?: (index: number) => unknown };
  if (typeof proto.at === 'function') return;
  Object.defineProperty(proto, 'at', {
    configurable: true,
    writable: true,
    value(this: unknown[], index: number) {
      const length = this.length >>> 0;
      let relative = Number(index);
      if (!Number.isFinite(relative)) return undefined;
      relative = Math.trunc(relative);
      const position = relative >= 0 ? relative : length + relative;
      if (position < 0 || position >= length) return undefined;
      return this[position];
    },
  });
}

function installStringReplaceAll(): void {
  const proto = String.prototype as unknown as {
    replaceAll?: (search: string | RegExp, replacement: string) => string;
  };
  if (typeof proto.replaceAll === 'function') return;
  Object.defineProperty(proto, 'replaceAll', {
    configurable: true,
    writable: true,
    value(this: string, search: string | RegExp, replacement: string) {
      const source = String(this);
      if (search instanceof RegExp) {
        if (!search.global) throw new TypeError('replaceAll 的正则必须是全局匹配');
        return source.replace(search, replacement);
      }
      return source.split(String(search)).join(String(replacement));
    },
  });
}

function installRandomUuid(): void {
  const cryptoObj = globalThis.crypto;
  if (!cryptoObj || typeof cryptoObj.randomUUID === 'function') return;
  try {
    Object.defineProperty(cryptoObj, 'randomUUID', {
      configurable: true,
      writable: true,
      value: () => formatRandomUuid(cryptoObj),
    });
  } catch {
    // 个别 WebView 的 crypto 不可扩展，调用方仍需自己判断方法是否存在。
  }
}

function formatRandomUuid(cryptoObj: Crypto): string {
  const bytes = new Uint8Array(16);
  if (typeof cryptoObj.getRandomValues === 'function') cryptoObj.getRandomValues(bytes);
  else {
    for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 根渲染失败后 Vue 只留下注释节点。此时给出可退出登录的说明，避免令牌还在、每次打开都是白屏。 */
export function recoverBlankPage(root: ParentNode | null = globalThis.document?.getElementById('app') ?? null): void {
  if (!root || root.querySelector('button, input, a, h1, h2')) return;
  const host = root as HTMLElement;
  host.replaceChildren();
  const page = host.ownerDocument.createElement('main');
  page.className = 'login-page';
  const card = host.ownerDocument.createElement('section');
  card.className = 'login-card';
  const title = host.ownerDocument.createElement('h1');
  title.textContent = '页面没有显示出来';
  const body = host.ownerDocument.createElement('p');
  body.textContent = '这台手机的网页组件较旧，进入工作台时出错。可以清除本机登录后重试。';
  const button = host.ownerDocument.createElement('button');
  button.className = 'primary-button';
  button.type = 'button';
  button.textContent = '清除登录并重试';
  button.addEventListener('click', () => {
    try {
      globalThis.localStorage?.removeItem('zhubao-auth-token');
    } catch {
      // 存储不可用时仍然刷新，避免停在空白页。
    }
    globalThis.location?.reload();
  });
  card.append(title, body, button);
  page.append(card);
  host.append(page);
}
