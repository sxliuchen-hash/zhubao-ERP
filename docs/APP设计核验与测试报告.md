# APP 设计核验与测试报告

核验日期：2026-10-14。对象为本地工作区，包含检查期间 Cursor 新提交的 `3219e6e`。本次未修改业务源码、未部署；安卓开发文档原有未提交修改保留。

## 1. 结论

**尚未完成原设计，不能作为正式交付版本验收。** 安卓调试容器、手机导航、工序百分比服务、备料部分服务、改单状态与部分差额入账、打印任务骨架已存在；业务页面、正式打印、权限、事务、数据库升级及真机验收仍有阻断。

基准为[安卓 APP 与集中打印开发文档](./安卓APP与集中打印开发文档.md)中的目标、业务设计、打印设计及验收清单，以及[订单变更与全链路追溯设计](./订单变更与全链路追溯设计.md)相关规则。文档中的“尚未实施”“仓库无 Android 工程”等旧事实已落后于代码，不能只凭文档或提交标题判断完成。

| 原设计 | 实际完成情况 |
|---|---|
| 签名 APK、实际 ERP 地址、覆盖升级 | 有 Android Debug 签名包；地址仍为 `https://erp.example.com`，正式签名及覆盖升级未验收 |
| 手机覆盖 16 页、按角色导航 | 16 页入口与全部功能导航已有；目标手机逐页作业未验收 |
| 扫码百分比、历史、全部可见订单、分页筛选 | 服务支持里程碑和历史；页面无相机及历史入口、无分页；未决定拆分的母单未进入新列表 |
| 备料收货、分批领用、公司料收发退回 | 部分后端存在；页面只有查询；公司料领用／退回与备料反向退回缺失 |
| 改单生效、执行轮次、财务逐次确认 | 状态和部分差额入账存在；实际岗位仍读原要求，页面未接通 |
| 标签版本、旧码校验、换贴、母单全追溯 | 有版本解析和简单时间线；正式标签仍为三段码，版本未接入关键作业，换贴未闭环 |
| 手机集中打印、状态、人工补打 | 前端未接入任务 API；服务有并发重复与过期状态问题 |
| 电脑打印尺寸、二维码、份数 | 代理发送占位字符串，没有实际标签渲染；真实出纸未验收 |
| MySQL 升级、重启与恢复 | 新迁移缺结构探针，目录加载被阻断；本次未做真实数据库升级／恢复 |

## 2. 核心缺陷与证据

以下 P1 影响业务正确性、权限或部署，足以阻止完成验收。源码位置以 `3219e6e` 为基准；路径均相对仓库根。

| 优先级与发现 | 证据及影响 | 验证方式 |
|---|---|---|
| P1：升级目录无法加载 | `backend/src/main/resources/db/mysql/upgrade-probes.tsv` 仅到 V48，索引／SQL 到 V53；`MigrationCatalog.java:128` 遇 V49 抛“缺少结构探针” | 4 项升级测试及 1 项财务 JDBC 测试报错 |
| P1：没有实际标签渲染及页面入口 | `PrintJobService.java:55` 快照仅 `LABEL\|单号\|模板\|份数\|V版本`；`print-agent/.../PrintAgent.java:96` 用 AUTOSENSE 发送该 UTF-8 原文，无尺寸／二维码／份数渲染；`LabelWorkspace.vue:280` 仍是浏览器打印，前端无 print-jobs 消费者 | 静态确认 |
| P1：同请求并发重复任务 | `PrintJobService.java:53` 先查后存；`InMemoryPrintJobRepository.java:30` 仅按任务 ID 保存。JDBC 重复键更新还覆盖状态／租约并返回传入的新 ID，可能返回未入库编号或重置已领取任务 | 内存两线程固定查重窗口后得到 2 条任务；JDBC 风险仅静态确认 |
| P1：打印读取及提交缺业务范围授权 | `PrintJobController.java:40`、`:47` 仅要求登录，即可读任意任务／全部任务；创建和补打不核验客户、真实单据、模板及原任务归属 | 静态确认，需 HTTP 越权回归 |
| P1：改单生效未切换生产事实 | `OrderChangeService.java:192` 只入账并保存 APPLIED；`ProcessStationService.java:554`、`:583` 仍读原母子单要求。没有有效路线／执行轮次，前端无改单 API 消费者 | 静态确认；现有状态测试未验证下一次岗位查询 |
| P1：改单缺范围授权及并发版本校验 | `OrderChangeService.java:55`、`:166`、`:206` 只检查登录；无客户／工序范围及非财务金额裁剪。草稿按数量加一分配版本，生效不检查当前订单预期版本 | 静态确认；缺跨客户／角色／请求及并发测试 |
| P1：改单与财务无法原子回滚 | `OrderChangeService.java:177` 工作单元未把会话传给写入；`JdbcOrderChangeRepository.java:51` 和 `JdbcFinanceRepository.java:178`、`:597`、`:906` 自行取连接，内存写入也未注册回滚 | 静态确认；应收成功后工资／结果保存失败可能留下半单 |
| P1：减额没有校验原应收归属 | `OrderChangePostingService.java:47` 调通用红字，`FinanceBillingService.java:153` 仅校验应收行和余额，未核对改单客户／母单／来源 | 静态确认；错误编号可能冲减另一客户账 |
| P1：备料和公司料不能从页面收发 | `PrepStockPanel.vue:25` 只 GET；全前端不调用新增收发 POST。`PrepStockService.java:119` 只有公司料入库，无领用／退回和备料反向退回 | 静态确认；内存服务测试不等于网页可操作 |
| P1：首次直接拍照未申请相机权限 | `MainActivity.kt:282` 直接 ACTION_IMAGE_CAPTURE，运行时授权仅在网页相机分支 `:258`，只捕获 ActivityNotFoundException | 静态确认，先拍照后扫码的表现待真机 |

其他需关闭的问题：

- **P2，动态复现：** 租约过期但仅调用 get/list，状态仍是 LEASED；只有下一次领取才触发 expireLeases，停机期间不能及时显示“不确定”。
- **P1，静态确认：** 改版后同版补打先创建当前版再改回旧快照，JDBC 更新不保存 content_snapshot；内存相同请求重放会内容冲突。两次保存间还可能被代理领取。
- **P2，静态确认：** 业务 fetch 401 仅抛错，未通知会话失效；原生只认主文档 401。500／503 也缺容器错误提示。
- **P2，静态确认：** 按顶层 URL 挂载 addJavascriptInterface 不能限制 iframe 的来源；桥当前没有前端消费者。应移除未使用桥或使用带来源约束的消息机制。参见 [Android WebView 官方文档](https://developer.android.com/reference/android/webkit/WebView#addJavascriptInterface(java.lang.Object,%20java.lang.String))。
- `ScanCodeParser.labelVersion()` 没有正式作业消费者，`LabelService.java:329` 仍生成三段码，旧标提示、关键作业版本核对及换贴没有形成闭环。

## 3. 实际测试记录

| 检查 | 结果 |
|---|---|
| 前端 pnpm test | 54 文件、260 项通过 |
| 前端 vue-tsc --noEmit | 通过 |
| 后端新增能力、权限、升级及应用装配定向测试 | 46 项：42 通过、4 报错；均为 V49 缺结构探针 |
| 后端改单财务、财务操作与 JDBC 补验 | 10 项：9 通过、1 报错；JDBC 初始化被同一迁移问题阻断 |
| 独立内存打印功能探针 | 2 项均复现缺陷：同请求并发为 2 任务；过期后查询仍是 LEASED |
| Android testDebugUnitTest | NO-SOURCE，没有测试用例，不能算行为验证通过 |
| Android lintDebug | 完整执行后 2 错误、7 警告，未通过 |
| 现存 APK 只读检查 | apksigner 校验通过；Android Debug 签名，com.zhubao.erp，versionCode 1，minSdk 26、targetSdk 37、debuggable；不是本次重新打包结果 |
| adb devices -l | 成功，设备列表为空；未安装或执行真机操作 |

后端合计 **56 项：51 通过、5 报错**。两组按顺序执行，JDK 为本机 JDK 21、编译目标 Java 17：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot'
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml '-Dtest=ProcessProgressServiceTest,PrepStockServiceTest,OrderChangeServiceTest,PrintJobServiceTest,ProgressBoardServiceTest,ScanCodeParserTest,BusinessReadAuthorizationTest,FinanceReadAuthorizationTest,FinanceWriteAuthorizationTest,DatabaseUpgradeServiceTest,ZhubaoErpApplicationTests,FinanceBillingServiceTest,FinancePayrollServiceTest' test
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml '-Dtest=OrderChangeFinanceTest,FinanceOperationServiceTest,JdbcFinanceRepositoryTest' test
```

第一组选择器中的 FinanceBillingServiceTest、FinancePayrollServiceTest 在仓库不存在，未计为通过；财务补验使用第二组存在的测试类。

Android lint 两项错误为：本机未入库 local.properties:1 的 Windows 路径冒号转义，以及 AndroidManifest.xml:6 的 CAMERA 声明缺相应可选硬件声明。后一项是兼容性静态检查，不能声称已在目标手机复现故障。debug 用户证书警告也不等于正式资源配置实际信任用户证书。

最初沙箱因依赖 realpath 返回 EPERM，前端测试未执行且类型检查产生依赖误报；已在正常本机环境重跑，上表采用重跑结果。Android 离线 lint 因依赖未缓存中断，在线补齐依赖后得到上述实际 lint 结果。

本机证据：日志 `.tmp-app-audit-{frontend,types,backend,finance,android-online,probe}.log`；复现源码 `output/app-audit/PrintAuditProbe.java`（使用本次 Surefire XML 的 java.class.path 与 JDK21 源文件启动）；Android 详细报告 `android-app/app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`。这些本机生成物未纳入 Git。

## 4. 关闭顺序与验收边界

先补迁移装配、数据权限、改单有效版本与财务原子提交；再补业务页面、真正标签渲染和打印并发／补打；修安卓授权与失效交互，配置真实地址和仓库外发布密钥。定向测试通过后，在隔离 MySQL 验证升级、回滚和重启，再做手机扫码／上传／覆盖升级、佳博尺寸与二维码出纸、断网和不确定任务核实、数据库与附件恢复。前端既有测试通过不能证明未接入入口可用；代码或历史构建不能代替现场结果。
