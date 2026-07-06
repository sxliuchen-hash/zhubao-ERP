package com.zhubao.erp.configuration.app;

import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.BackupPolicy;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.CodeRule;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ComponentBaseline;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ConfigurationBaselineSummary;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ProcessTemplate;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.RolePermissionBaseline;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.SystemParameter;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.VideoParameter;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.WarehouseState;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConfigurationBaselineService {

    private final List<SystemParameter> systemParameters = List.of(
            new SystemParameter("system.code.prefix", "JG", "编码", "C2", "二维码和业务编码系统前缀", "管理员变更，记录日志", "全局"),
            new SystemParameter("weight.precision", "3", "称重", "C1", "重量小数位，影响台账和损耗计算", "需审批，保留历史", "库存/工序"),
            new SystemParameter("split.weight.tolerance", "0.02", "称重", "C1", "工单拆分重量误差阈值", "需审批，保留历史", "母单拆分"),
            new SystemParameter("loss.approval.enabled", "true", "损耗", "C1", "超标损耗是否触发审批", "需审批，保留历史", "工序报工/质检"),
            new SystemParameter("rework.max.count", "2", "返工", "C1", "单张工单允许返工次数上限", "需审批，保留历史", "质检返工"),
            new SystemParameter("inventory.negative.allowed", "false", "库存", "C1", "一期不允许负库存", "需审批，保留历史", "库存台账"),
            new SystemParameter("pay.formal.enabled", "false", "工资", "C1", "一期只做工资试算，不做正式发放", "需审批，保留历史", "工资试算")
    );

    private final List<CodeRule> codeRules = List.of(
            new CodeRule("客户订单", "ORD-YYYYMMDD-流水号", "ORD-20260706-001", "全局唯一，作废不复用", "M3 中期冻结"),
            new CodeRule("母单", "CC-YYYYMMDD-流水号-客户简称", "CC-20260706-001-JL", "按业务日期唯一", "M3 中期冻结"),
            new CodeRule("子工单", "母单编码-ZD流水号", "CC-20260706-001-JL-ZD001", "同母单内唯一", "M3 中期冻结"),
            new CodeRule("返工工单", "原工单编码-FG流水号", "CC-20260706-001-JL-ZD001-FG001", "来源工单内唯一", "M3 中期冻结"),
            new CodeRule("库存台账", "STK-YYYYMMDD-流水号", "STK-20260706-001", "全局唯一，只追加", "M3 中期冻结")
    );

    private final List<RolePermissionBaseline> rolePermissions = List.of(
            new RolePermissionBaseline("系统管理员", "全局", List.of("用户", "角色", "菜单", "参数", "字典", "日志"), List.of("角色权限", "参数修改", "数据字典")),
            new RolePermissionBaseline("收货员", "收货工位", List.of("来料入库", "母单标签", "拆包视频"), List.of("标签补打", "视频人工上传")),
            new RolePermissionBaseline("车间组长", "本班组", List.of("工单拆分", "分配", "返工处理", "称重交接"), List.of("报工更正", "超标损耗审批")),
            new RolePermissionBaseline("操作工", "本人任务", List.of("扫码报工", "称重交接", "附件上传"), List.of("报工提交", "称重提交")),
            new RolePermissionBaseline("质检员", "质检队列", List.of("待质检", "质检记录", "返工/报废发起"), List.of("报废发起", "返工判定")),
            new RolePermissionBaseline("库管员", "仓库", List.of("库存查询", "出库", "打包", "库存调整申请"), List.of("库存调整申请", "出库确认"))
    );

    private final List<ProcessTemplate> processTemplates = List.of(
            new ProcessTemplate(
                    "PROC-RING-STD",
                    "戒托标准加工",
                    "18K 金戒托 / PT950 吊坠坯",
                    List.of("粗抛", "精抛", "镶石", "终检入库"),
                    "抛光工序标准损耗 2.0%，超标进入审批",
                    "工序单价按工单创建时快照，一期仅工资试算",
                    "镶石和终检必须质检",
                    "物料单价、工序单价、损耗率、客户归属"
            ),
            new ProcessTemplate(
                    "PROC-CHAIN-STD",
                    "银链标准加工",
                    "银链半成品",
                    List.of("焊接", "打磨", "清洗", "终检入库"),
                    "焊接标准损耗 0.8%，缺少称重阻断提交",
                    "焊接和打磨计件，清洗不计件",
                    "焊接缺陷需拍照，终检后转成品",
                    "工序单价、损耗率、质检规则"
            )
    );

    private final List<WarehouseState> warehouseStates = List.of(
            new WarehouseState("RAW-CUSTOMER", "待加工来料仓", "客户所有", "待加工", "来料入库：外部 -> 待加工", false),
            new WarehouseState("WIP", "在制仓", "客户所有", "在制", "拆分工单：待加工 -> 在制", false),
            new WarehouseState("FINISHED", "成品仓", "客户所有", "成品", "完工入库：在制 -> 成品", false),
            new WarehouseState("FROZEN", "冻结仓", "客户所有", "冻结", "创建出库：成品 -> 冻结", false),
            new WarehouseState("REWORK", "返工品仓", "客户所有", "返工", "质检返工：成品/在制 -> 返工", false),
            new WarehouseState("SCRAP", "报废品仓", "客户所有", "报废", "质检报废：对应状态 -> 报废", false)
    );

    private final List<VideoParameter> videoParameters = List.of(
            new VideoParameter("video.buffer.seconds", "5", "拆包/打包/异常处理", "人工上传 MP4", "待现场确认", "管理员变更，记录日志"),
            new VideoParameter("video.retry.max", "3", "NVR 时间段截取", "失败进入人工上传队列", "待现场确认", "管理员变更，记录日志"),
            new VideoParameter("video.default.format", "mp4", "业务片段", "原始文件留存路径", "按对象存储策略", "版本记录"),
            new VideoParameter("video.upload.max.size", "待确认", "人工上传", "超限走离线归档", "待现场确认", "需审批"),
            new VideoParameter("video.camera.binding", "工位/业务点", "拆包台、打包台、异常处理台", "未绑定时允许人工附件", "长期有效", "需审批")
    );

    private final List<ComponentBaseline> components = List.of(
            new ComponentBaseline("Spring Boot", "3.3.7", "Apache-2.0", "业务 API", "Maven Central", "版本随后端安全补丁评估"),
            new ComponentBaseline("Vue", "3.5.x", "MIT", "PC 后台和 PWA", "npm registry", "组件库待冻结"),
            new ComponentBaseline("RuoYi-Vue", "待冻结", "MIT", "权限、菜单、字典参考基座", "GitHub", "需确认具体分支和二开边界"),
            new ComponentBaseline("FFmpeg", "待冻结", "LGPL/GPL 需确认", "视频截取", "ffmpeg.org", "需确认二进制许可证策略"),
            new ComponentBaseline("ZXing", "待冻结", "Apache-2.0", "扫码兜底", "GitHub/Maven/npm", "移动端兼容性需 POC")
    );

    private final List<BackupPolicy> backupPolicies = List.of(
            new BackupPolicy("业务数据库", "每日全量 + binlog 增量", "试点 24 小时，正式 15-60 分钟", "试点 1 个工作日，正式 4 小时", "运维"),
            new BackupPolicy("视频附件", "NAS 快照或对象存储生命周期", "24 小时", "1 个工作日", "运维/库管"),
            new BackupPolicy("配置和模板", "每次变更生成版本记录", "变更即备份", "4 小时", "系统管理员")
    );

    public ConfigurationBaselineSummary summary() {
        return new ConfigurationBaselineSummary(
                systemParameters.size(),
                codeRules.size(),
                rolePermissions.size(),
                processTemplates.size(),
                warehouseStates.size(),
                videoParameters.size(),
                approvalRequiredItems()
        );
    }

    public List<SystemParameter> systemParameters() {
        return systemParameters;
    }

    public List<CodeRule> codeRules() {
        return codeRules;
    }

    public List<RolePermissionBaseline> rolePermissions() {
        return rolePermissions;
    }

    public List<ProcessTemplate> processTemplates() {
        return processTemplates;
    }

    public List<WarehouseState> warehouseStates() {
        return warehouseStates;
    }

    public List<VideoParameter> videoParameters() {
        return videoParameters;
    }

    public List<ComponentBaseline> components() {
        return components;
    }

    public List<BackupPolicy> backupPolicies() {
        return backupPolicies;
    }

    private int approvalRequiredItems() {
        long parameterApprovals = systemParameters.stream()
                .filter(parameter -> parameter.changeControl().contains("审批"))
                .count();
        long videoApprovals = videoParameters.stream()
                .filter(parameter -> parameter.changeControl().contains("审批"))
                .count();
        return Math.toIntExact(parameterApprovals + videoApprovals);
    }
}
