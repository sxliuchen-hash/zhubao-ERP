import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import { ApiRequestError, getAuthToken } from '../../services/apiClient';

type Fields = Record<string, Ref<string>>;
interface Command {
  requestId: string;
  payload: string;
  phase: 'rejected' | 'pending' | 'unknown' | 'done';
  fields: Record<string, string>;
  result?: unknown;
}
interface Draft { fields: Record<string, string>; commands: Map<string, Command> }

// 与已有整袋草稿一致，当前页面会话内按账号、业务页和单号保留，切单或重新打开组件不串值。
const drafts = new Map<string, Draft>();
const revision = ref(0);

export function useBusinessCommand(namespace: string, target: Ref<string>, fields: Fields, message: Ref<string>) {
  const owner = getAuthToken();
  const defaults = snapshot();
  let restoring = false;
  const key = (code: string) => `${owner}\n${namespace}\n${code.trim()}`;
  function snapshot() { return Object.fromEntries(Object.entries(fields).map(([name, field]) => [name, field.value])); }
  function draft(code = target.value): Draft {
    const id = key(code);
    let value = drafts.get(id);
    if (!value) { value = { fields: { ...defaults }, commands: new Map() }; drafts.set(id, value); }
    return value;
  }
  function restore(values: Record<string, string>) {
    restoring = true;
    for (const [name, field] of Object.entries(fields)) field.value = values[name] ?? defaults[name];
    restoring = false;
  }
  watch(target, (code, previous) => {
    draft(previous).fields = snapshot();
    restore(draft(code).fields);
    message.value = '';
  }, { flush: 'sync' });
  watch(() => Object.values(fields).map(field => field.value), () => {
    if (!restoring) draft().fields = snapshot();
  }, { flush: 'sync' });
  onBeforeUnmount(() => { draft().fields = snapshot(); });
  const pending = computed(() => { revision.value; return [...draft().commands.values()].some(command => command.phase === 'pending'); });
  const hasUnconfirmed = computed(() => { revision.value; return [...draft().commands.values()].some(command => command.phase === 'unknown'); });

  async function submit<Body extends object, Result>(action: string, body: Body,
      send: (code: string, request: Body & { requestId: string }) => Promise<Result>): Promise<Result | null> {
    if (pending.value) return null;
    if (getAuthToken() !== owner) { message.value = '账号已变化，请重新打开办理页'; return null; }
    const code = target.value.trim();
    if (!code) { message.value = '请先填写单号'; return null; }
    const current = draft(code);
    const payload = JSON.stringify(body);
    let command = current.commands.get(action);
    const unknown = [...current.commands.entries()].find(([, item]) => item.phase === 'unknown');
    if (unknown && (unknown[0] !== action || unknown[1].payload !== payload)) {
      message.value = '上次办理结果待确认，请恢复上次填写并重试原操作'; return null;
    }
    if (command?.phase === 'done') {
      if (command.payload === payload) return command.result as Result;
      message.value = '本笔已完成，请先开始下一笔'; return null;
    }
    if (!command || command.payload !== payload) {
      const random = [...crypto.getRandomValues(new Uint32Array(4))].map(part => part.toString(16).padStart(8, '0')).join('');
      command = { requestId: `${namespace}-${random}`, payload, phase: 'rejected', fields: snapshot() };
      current.commands.set(action, command);
    }
    command.phase = 'pending';
    revision.value++;
    try {
      const result = await send(code, { ...body, requestId: command.requestId });
      command.result = result;
      command.phase = 'done';
      return key(target.value) === key(code) ? result : null;
    } catch (error) {
      command.phase = error instanceof ApiRequestError && error.status >= 400 && error.status < 500 ? 'rejected' : 'unknown';
      if (key(target.value) === key(code)) message.value = error instanceof Error ? error.message : '办理失败，请重试';
      return null;
    } finally { revision.value++; }
  }

  function restorePending() {
    const command = [...draft().commands.values()].find(item => item.phase === 'unknown');
    if (command) { restore(command.fields); message.value = '已恢复上次填写，请重试原操作'; }
  }
  function startNext() {
    if (pending.value || hasUnconfirmed.value) { message.value = '请先确认上次办理结果'; return; }
    draft().commands.clear();
    revision.value++;
    draft().fields = snapshot();
    message.value = '已开始下一笔，请核对本次填写';
  }
  return { pending, hasUnconfirmed, submit, restorePending, startNext };
}
