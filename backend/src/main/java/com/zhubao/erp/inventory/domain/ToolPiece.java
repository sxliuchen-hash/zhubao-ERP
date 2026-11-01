package com.zhubao.erp.inventory.domain;

/** 工具的实际编号、库位与保管状态，只读展示不改变存量。 */
public record ToolPiece(String pieceCode,String itemId,String warehouseCode,String holderId,boolean scrapped) {}
