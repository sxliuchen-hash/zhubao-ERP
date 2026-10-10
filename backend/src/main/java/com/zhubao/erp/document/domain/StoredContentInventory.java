package com.zhubao.erp.document.domain;

import java.util.List;

/**
 * 附件目录占用和所在磁盘余量，供管理员查看存储内容。
 */
public record StoredContentInventory(
        String provider,
        String rootPath,
        int fileCount,
        long usedBytes,
        String usedLabel,
        long diskTotalBytes,
        long diskFreeBytes,
        int diskUsedPercent,
        List<StoredContentItem> items
) {
    public StoredContentInventory {
        items = List.copyOf(items);
    }
}
