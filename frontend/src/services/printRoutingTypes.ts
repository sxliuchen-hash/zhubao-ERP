export interface PrintQueueView {
  stationId: string; queueId: string; name: string; labelType: string;
  widthMm: number; heightMm: number; enabled: boolean; defaultQueue: boolean;
}
export interface PrintTargetJobView {
  id: string; targetStationId: string; targetQueueId: string; status: string;
  failureReason: string; paperCheckResult: string; copies: number; predecessorId: string; retryReason: string;
}
export interface PrintBatchView { id: string; jobs: PrintTargetJobView[]; contentSnapshot?: string; businessCode?: string; templateCode?: string }
export interface PrintBatchCommand {
  requestId: string; businessCode: string; templateCode: string; copiesPerTarget: number;
  targets: Array<{ stationId: string; queueId: string }>;
}
