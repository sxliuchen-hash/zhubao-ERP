package com.zhubao.erp.business.app;

/**
 * 普通加工的执行号。空执行号和等于母单号的执行号都属于不拆母单，但两者仍分开记录。
 */
public final class ProcessingTargetResolver {

    public enum ExecutionIdentity {
        EMPTY,
        MOTHER,
        CHILD
    }

    private ProcessingTargetResolver() {
    }

    public static ExecutionIdentity identity(String motherCode, String workOrderCode) {
        String mother = text(motherCode);
        String work = text(workOrderCode);
        if (work.isEmpty()) {
            return ExecutionIdentity.EMPTY;
        }
        if (work.equals(mother)) {
            return ExecutionIdentity.MOTHER;
        }
        return ExecutionIdentity.CHILD;
    }

    public static boolean keptMotherStock(String motherCode, String workOrderCode) {
        ExecutionIdentity identity = identity(motherCode, workOrderCode);
        return identity == ExecutionIdentity.EMPTY || identity == ExecutionIdentity.MOTHER;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
