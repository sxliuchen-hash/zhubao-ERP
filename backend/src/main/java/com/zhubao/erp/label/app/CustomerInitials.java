package com.zhubao.erp.label.app;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;

/**
 * 客户名称的拼音首字母。供标签模板暗记使用，不印出中文名。
 */
public final class CustomerInitials {

    private CustomerInitials() {
    }

    public static String of(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String raw = PinyinHelper.toPinyin(name.trim(), PinyinStyleEnum.FIRST_LETTER, " ");
        StringBuilder letters = new StringBuilder();
        for (String token : raw.split("\\s+")) {
            String latin = token.replaceAll("[^A-Za-z]", "");
            if (latin.isEmpty()) {
                continue;
            }
            letters.append(Character.toUpperCase(latin.charAt(0)));
            if (letters.length() == 8) {
                break;
            }
        }
        return letters.toString();
    }
}
