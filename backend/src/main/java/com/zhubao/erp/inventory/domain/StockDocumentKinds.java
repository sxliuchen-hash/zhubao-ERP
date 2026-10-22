package com.zhubao.erp.inventory.domain;

public final class StockDocumentKinds {
    public static final String COMPANY_RECEIPT = "COMPANY_RECEIPT";
    public static final String COMPANY_ISSUE = "COMPANY_ISSUE";
    public static final String COMPANY_RETURN = "COMPANY_RETURN";
    public static final String PREP_RECEIPT = "PREP_RECEIPT";
    public static final String PREP_ISSUE = "PREP_ISSUE";
    public static final String PREP_RETURN = "PREP_RETURN";
    public static final String PREP_CUSTOMER_RETURN = "PREP_CUSTOMER_RETURN";
    public static final String FREEZE = "FREEZE";
    public static final String UNFREEZE = "UNFREEZE";
    public static final String CORRECTION = "CORRECTION";
    public static final String PENDING = "PENDING";
    public static final String POSTED = "POSTED";
    public static final String IN = "IN";
    public static final String OUT = "OUT";

    private StockDocumentKinds() {
    }
}
