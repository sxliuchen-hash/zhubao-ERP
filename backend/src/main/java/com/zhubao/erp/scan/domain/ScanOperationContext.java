package com.zhubao.erp.scan.domain;

import java.util.List;

/**
 * 正式操作所需的维度和事实快照。不返回可由客户端任意执行的接口地址。
 */
public record ScanOperationContext(
        List<String> formalOperations,
        List<ScanBalanceCandidate> balanceCandidates,
        boolean balanceSelectionRequired,
        ScanBalanceCandidate selectedBalance,
        String factSnapshot
) {
    public ScanOperationContext {
        formalOperations = formalOperations == null ? List.of() : List.copyOf(formalOperations);
        balanceCandidates = balanceCandidates == null ? List.of() : List.copyOf(balanceCandidates);
    }
}
