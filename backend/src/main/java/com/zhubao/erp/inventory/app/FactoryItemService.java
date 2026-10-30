package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemPolicy;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 本厂物品档案。只记录类别、规格身份和已选定的计耗或保管方式，不写库存、采购或客户账。
 */
@Service
public class FactoryItemService {

    private final FactoryItemRepository items;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public FactoryItemService(FactoryItemRepository items, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.items = items;
        this.unit = unit;
        this.permissions = permissions;
    }

    public FactoryItem register(String requestId, OperatorContext actor, FactoryItemCategory category, String materialName,
                                String specCode, String unitName, ConsumptionTiming timing, CustodyMode custody) {
        maintain(actor);
        String name = required(materialName, "请填写材质或品名", 80);
        String spec = required(specCode, "请填写可独立领用的规格", 40);
        String unitText = required(unitName, "请填写单位", 16);
        ConsumptionTiming resolvedTiming = FactoryItemPolicy.timing(category, timing);
        CustodyMode resolvedCustody = FactoryItemPolicy.custody(category, custody);
        String identity = FactoryItemPolicy.identity(category, name, spec, unitText);
        String itemId = unit.execute(new CommandCall(
                requestId,
                actor.operator(),
                "REGISTER_FACTORY_ITEM",
                "FACTORY_ITEM",
                commandTarget(identity),
                payload(category, name, spec, unitText, resolvedTiming, resolvedCustody)
        ), session -> {
            if (items.findIdentity(category, name, spec, unitText).isPresent()) {
                throw new BusinessException("同一类别、材质、规格和单位已经建档，不能合并");
            }
            String id = "FI-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
            items.insert(new FactoryItem(id, category, name, spec, unitText, resolvedTiming, resolvedCustody, true, 1), session);
            return id;
        });
        return require(itemId);
    }

    public FactoryItem recordPolicy(String requestId, OperatorContext actor, String itemId, int version, String materialName,
                                    ConsumptionTiming timing, CustodyMode custody) {
        maintain(actor);
        FactoryItem current = require(itemId);
        if (materialName != null && !materialName.isBlank() && !current.materialName().equals(materialName.trim())) {
            throw new BusinessException("材质名称属于物品身份，不能改成另一条");
        }
        ConsumptionTiming resolvedTiming = FactoryItemPolicy.timing(current.category(), timing);
        CustodyMode resolvedCustody = FactoryItemPolicy.custody(current.category(), custody);
        String saved = unit.execute(new CommandCall(
                requestId,
                actor.operator(),
                "RECORD_FACTORY_ITEM_POLICY",
                "FACTORY_ITEM",
                current.itemId(),
                payload(current.category(), current.materialName(), current.specCode(), current.unit(), resolvedTiming, resolvedCustody) + "\n" + version
        ), session -> {
            FactoryItem locked = items.find(current.itemId()).orElseThrow(() -> new BusinessException("物品档案不存在"));
            if (locked.version() != version) {
                throw new BusinessException("物品档案已被其他人修改，请刷新后重试");
            }
            if (!locked.materialName().equals(current.materialName()) || !locked.specCode().equals(current.specCode())
                    || !locked.unit().equals(current.unit()) || locked.category() != current.category()) {
                throw new BusinessException("规格身份不能改，同材质的不同规格不能合并");
            }
            FactoryItem next = new FactoryItem(
                    locked.itemId(), locked.category(), locked.materialName(), locked.specCode(), locked.unit(),
                    resolvedTiming, resolvedCustody, locked.enabled(), version + 1);
            items.updatePolicy(next, version, session);
            return locked.itemId() + "@" + next.version();
        });
        return require(saved.substring(0, saved.indexOf('@')));
    }

    /** 按材质名称列出档案。同名不同规格都返回，查询不建档。 */
    public List<FactoryItem> list(OperatorContext actor, String materialName) {
        maintain(actor);
        return items.list(materialName);
    }

    public FactoryItem find(OperatorContext actor, String itemId) {
        maintain(actor);
        return require(itemId);
    }

    private void maintain(OperatorContext actor) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.FACTORY_ITEM_MANAGE, actor);
    }

    private FactoryItem require(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessException("物品档案不存在");
        }
        return items.find(itemId.trim()).orElseThrow(() -> new BusinessException("物品档案不存在"));
    }

    private static String payload(FactoryItemCategory category, String name, String spec, String unit,
                                  ConsumptionTiming timing, CustodyMode custody) {
        return category.name() + "\n" + name + "\n" + spec + "\n" + unit + "\n" + timing.name() + "\n" + custody.name();
    }

    /** 命令目标有长度上限。身份原文放在请求内容里，目标只用稳定摘要定位。 */
    private static String commandTarget(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 20);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String required(String value, String message, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > max) {
            throw new BusinessException(message.replace("请填写", "") + "过长");
        }
        return text;
    }
}
