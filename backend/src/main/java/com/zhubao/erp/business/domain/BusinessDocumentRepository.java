package com.zhubao.erp.business.domain;

import java.util.Optional;

public interface BusinessDocumentRepository {

    boolean motherOrderExists(String code);

    MotherOrderRecord saveMotherOrder(MotherOrderRecord record);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code);

    boolean outboundOrderExists(String code);

    OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record);

    Optional<OutboundOrderRecord> findOutboundOrderByCode(String code);
}
