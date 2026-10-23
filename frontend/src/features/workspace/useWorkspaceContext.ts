import { inject } from 'vue';
import { createWorkspaceState, type WorkspaceState } from './workspaceContext';

/**
 * 读取当前应用提供的工作区上下文。
 * 单独挂载的测试没有外壳时，使用一份不会写回真实会话的临时状态。
 */
export function useWorkspaceContext(): WorkspaceState {
  return inject<WorkspaceState | null>('zhubao-workspace-context', null) ?? createWorkspaceState();
}
