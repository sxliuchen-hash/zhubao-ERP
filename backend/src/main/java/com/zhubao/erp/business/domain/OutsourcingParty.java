package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.regex.Pattern;

/**
 * 外协方的稳定身份和当前名称。没有登录账号，改名不改变编号。
 */
public record OutsourcingParty(String partyId, String name, int version) {

    private static final Pattern PARTY_ID = Pattern.compile("^WX-[A-Za-z0-9][A-Za-z0-9._:-]{0,36}$");

    public OutsourcingParty {
        partyId = requireId(partyId);
        name = requireName(name);
        if (version < 1) {
            throw new BusinessException("外协方版本不正确");
        }
    }

    public static OutsourcingParty create(OutsourcingCounterpart counterpart, String partyId, String name) {
        if (counterpart == null) {
            throw new BusinessException("外协方不能登记成客户或员工");
        }
        counterpart.assertParty();
        return new OutsourcingParty(partyId, name, 1);
    }

    public OutsourcingParty rename(String nextName) {
        return new OutsourcingParty(partyId, nextName, version + 1);
    }

    public static String requireId(String partyId) {
        String id = partyId == null ? "" : partyId.trim();
        if (!PARTY_ID.matcher(id).matches()) {
            throw new BusinessException("外协方缺少稳定身份");
        }
        return id;
    }

    public static String requireName(String name) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty()) {
            throw new BusinessException("外协方名称不能为空");
        }
        if (text.length() > 80) {
            throw new BusinessException("外协方名称过长");
        }
        return text;
    }
}
