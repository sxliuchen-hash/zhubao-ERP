package com.zhubao.erp.label.domain;

/** 浏览器生成的最终打印画面及其对应的标签内容指纹。 */
public record LabelPrintImage(String snapshotHash, String pngBase64) { }
