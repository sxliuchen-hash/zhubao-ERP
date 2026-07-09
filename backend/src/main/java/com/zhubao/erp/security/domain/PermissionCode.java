package com.zhubao.erp.security.domain;

public enum PermissionCode {
    INVENTORY_LEDGER_TRANSFER("库存台账直接转移"),
    BUSINESS_INBOUND_CONFIRM("来料确认入库"),
    BUSINESS_PRODUCTION_REPORT("生产报工"),
    BUSINESS_QUALITY_FREEZE("质检冻结库存"),
    BUSINESS_REWORK_DISPATCH("返工派发"),
    BUSINESS_REWORK_REPORT("返工报工"),
    BUSINESS_QUALITY_RELEASE("复检放行"),
    BUSINESS_OUTBOUND_SHIPMENT("成品出库"),
    BUSINESS_CUSTOMER_RETURN("客户退货入库"),
    BUSINESS_AFTER_SALES_REVIEW("售后复核分流"),
    BUSINESS_LOSS_APPROVAL("损耗审批试算"),
    REGISTER_IMPORT_REVIEW("登记表复核补录"),
    REGISTER_IMPORT_POSTING("登记表批次入账"),
    MACHINE_OUTPUT_SUBMIT("设备产量提交"),
    GATEWAY_READING_CONSUME("设备读数消费"),
    GATEWAY_DEVICE_MANAGE("设备网关管理"),
    AUTH_ACCOUNT_MANAGE("登录账号管理"),
    CUSTOMER_AUTHORIZATION_MANAGE("客户授权管理"),
    APPROVAL_DECISION("审批任务决策"),
    DOCUMENT_ATTACHMENT_UPLOAD("证据附件上传"),
    VIDEO_TASK_OPERATE("视频任务操作"),
    LABEL_PRINT("标签打印");

    private final String label;

    PermissionCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
