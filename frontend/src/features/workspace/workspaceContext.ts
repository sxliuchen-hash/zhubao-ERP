import { ref, type Ref } from 'vue';
import type { ScanObjectType } from '../../services/scanContextApi';
import type { WorkspaceSection } from './workspaceNavigation';

/** 当前选中的规范身份。selectionRevision 只是本页查询代次，不能当作业务版本。 */
export interface OrderSelection {
  objectType: ScanObjectType;
  code: string;
  motherOrderCode: string | null;
  workOrderCode: string | null;
  executionRoundCode: string | null;
  selectionRevision: number;
}

/** 从详情返回列表时恢复的筛选位置。不写入 localStorage。 */
export interface OrderReturnLocation {
  section: WorkspaceSection;
  view?: 'ORDERS' | 'PRODUCTION' | 'QUALITY';
  query: string;
  customerId: string;
  process: string;
  facet: string;
  dueBefore: string;
  page: number;
  scrollTop: number;
}

/** 结果未知的动作留在会话里，关闭弹层不能把它丢掉。 */
export interface PendingActionSession {
  draftKey: string;
  requestId: string;
}

export interface WorkspaceState {
  selection: Ref<OrderSelection | null>;
  returnLocation: Ref<OrderReturnLocation | null>;
  pendingAction: Ref<PendingActionSession | null>;
  rememberSelection: (input: Omit<OrderSelection, 'selectionRevision'>) => void;
  rememberReturn: (location: OrderReturnLocation) => void;
  holdPendingAction: (action: PendingActionSession) => void;
  clear: () => void;
}

/** 每个应用实例一份。退出、换账号或权限变化时调用 clear。 */
export function createWorkspaceState(): WorkspaceState {
  const selection = ref<OrderSelection | null>(null);
  const returnLocation = ref<OrderReturnLocation | null>(null);
  const pendingAction = ref<PendingActionSession | null>(null);
  let revision = 0;

  function rememberSelection(input: Omit<OrderSelection, 'selectionRevision'>) {
    revision += 1;
    selection.value = { ...input, selectionRevision: revision };
  }

  function rememberReturn(location: OrderReturnLocation) {
    returnLocation.value = { ...location };
  }

  function holdPendingAction(action: PendingActionSession) {
    pendingAction.value = { ...action };
  }

  function clear() {
    revision += 1;
    selection.value = null;
    returnLocation.value = null;
    pendingAction.value = null;
  }

  return {
    selection,
    returnLocation,
    pendingAction,
    rememberSelection,
    rememberReturn,
    holdPendingAction,
    clear,
  };
}
