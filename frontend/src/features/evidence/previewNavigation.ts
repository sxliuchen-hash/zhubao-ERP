export function previewTarget(userAgent = globalThis.navigator?.userAgent ?? ''): '_self' | '_blank' {
  return /ZhubaoErpShell\//.test(userAgent) ? '_self' : '_blank';
}
