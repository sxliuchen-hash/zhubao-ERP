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

## 5. Cursor 修复后的第二次核验

2026-10-14 复核 `7646bb4`，覆盖 `a5a2c94`、`e70c525`、`90868f7`、`7646bb4` 等修复提交。前四节保留首次核验事实，本节为最新结果。**已有实质修复，上一轮提示词仍未全部完成；剩余内容包含代码和页面问题，不只是等待真实设备。** 本次未修改业务源码。

### 5.1 已确认改善

- V49—V55 迁移目录及探针可装载，升级测试和财务 JDBC 测试通过。
- 岗位查询已读取有效要求、期限和路线；改单版本、状态及财务写入进入同一工作会话；减额已校验原应收归属。新增 H2 和内存回滚测试有实质覆盖。
- 备料及公司料已增加收发退回入口，进度增加筛选分页和历史查询代码。
- 打印已有 PNG／二维码渲染、份数设置、同请求唯一插入、租约过期处理及客户范围校验，不再只是原文占位字符串。
- 安卓直接拍照前已申请权限，原生桥移除，主文档 500／503 有提示，lint 通过，正式构建有签名和地址校验。

### 5.2 尚未关闭的关键问题

| 优先级 | 问题、触发与证据 | 本次证据类别 |
|---|---|---|
| P1 | **进度定位没有执行。** `ProcessProgressPanel.vue:145` 监听 ScanInput 的 draft；真正点击定位和相机成功发的是 recognize（`ScanInput.vue:75`、`:94`）。输入单号后点击定位，目标查询接口调用次数实际为 0 | 组件测试复现 |
| P1 | **备料失败重试可能重复入账。** `PrepStockPanel.vue:33` 每次点击以 Date.now 生成新请求号，`:90–95` 六种操作均如此。模拟响应丢失后重试，编号从 prep-recv-10000000 变为 prep-recv-10000001，服务端无法将其识别为同请求 | 组件测试复现编号变化；重复记账风险来自幂等契约 |
| P1 | **集中打印点击重试也更换请求号。** `LabelWorkspace.vue:306`、`:332` 每次生成新号，按钮没有提交中禁用。首次已保存但响应丢失时，重试成为另一条可打印任务 | 静态确认 |
| P1 | **财务确认和生效仍不能从页面完成。** 全前端改单消费者只在 `ProcessProgressPanel.vue:113–134`，只有申请、送财务和时间线；没有 finance-confirmations／finance-rejections／applications 消费者，亦没有路线及重做工序编辑入口 | 接口／页面调用搜索确认 |
| P1 | **执行轮次只有记录，不能实际重做报工。** `OrderChangeService.java:343` 保存 RW 轮次；`ProcessStationService.java:563–584` 只解析原母子单，`:316` 仍按原工单＋工序拒绝已交工序。新增测试只验证轮次存在，未交付新轮次 | 静态确认及测试覆盖核对 |
| P1 | **关键作业丢弃扫码版本。** 生产 `ProductionPanel.vue:168`、`:254` 和质检 `QualityPanel.vue:210`、`:298` 将规范单号提交；发货也未提交原二维码。`LabelVersionGuard.java:35` 对非 JG 原码直接返回，旧标／未知版实际可绕过新校验 | 静态确认实际调用链 |
| P1 | **实际会话失效仍不回登录。** 后端 `AuthSessionService.java:430`、`:443`、`:455`、`:478` 抛权限异常，经 `GlobalExceptionHandler.java:28` 返回 403；`apiClient.ts:38` 只清 401。模拟该 403 后旧 token 保留。应区分身份失效与普通无权限，不能一律清空所有 403 | 后端契约静态确认＋前端响应测试复现 |
| P1 | **陈旧改单可覆盖草稿基准版本。** `OrderChangeService.java:247` 优先采纳调用方 expectedVersion，而非固定草稿已确认基准。两张 V0 草稿，第一张生效后第二张传 1 即可绕过基准冲突，无需重新财务确认 | 静态确认；现有测试只传旧值 0 |
| P1 | **连续改单可能恢复旧路线。** `OrderChangeService.java:120`、`:433–440` 默认路线读取原单；`:136`、`:138` 的旧要求／期限也来自原母单。第一次改路线后第二次只改期限，遗漏 newRoute 会回到原路线，历史前值也不正确 | 静态确认；缺连续改单回归 |
| P1 | **工序范围与草稿并发仍不完整。** `OrderChangeService.java:123` 仅核验 reworkProcess，没有核验 newRoute 的受影响工序；`:115` 先查请求再存，`:131` 按数量分配版本。JDBC 重复键保存 `JdbcOrderChangeRepository.java:39`、`:59` 仍可能返回未入库的新 ID | 静态确认；现有并发测试仅覆盖既有改单生效 |
| P1 | **集中标签仍不等于预览模板。** `PrintJobService.java:216–227` 未取实际 LabelService／模板字段，固定 60×40；`LabelRenderer.java:39` 未画客户、要求、期限等预览内容。代理只设 Copies，未设纸张／可打印区域／分辨率；PNG 像素尺寸不能证明实际毫米尺寸 | 静态确认；未实际出纸 |
| P1 | **换贴未形成页面与母单追溯闭环。** `PrintJobService.java:148`、`:159`、`:170` 把子单业务号同时写成母单号，真正母单查询不到这些标签事件；前端没有 paper-checks／relabels 消费者。时间线仍是部分文字集合，未聚合设计中的库存、质检、发货和照片视频链接 | 静态确认 |

另有两项需处理：代理 `PrintAgent.java:129` 忽略回报 HTTP 状态，失败不记录任务号或重试回报；安卓拒绝相机后已清空待拍照动作和文件回调（`MainActivity.kt:79–93`），重试只重新授权（`:336`），授权成功也不能继续原操作。

### 5.3 本次重新执行的验证

| 检查 | 实际结果 |
|---|---|
| 前端原有回归 | 54 文件／260 项通过 |
| 前端 TypeScript | 通过 |
| 后端定向测试 | 19 个类／77 项：72 通过、2 断言失败、3 初始化报错 |
| 额外组件与会话探针 | 2 文件／3 项均复现缺陷：定位未调用、重试换号、403 保留旧会话 |
| print-agent 渲染测试 | 1 项通过，不代表实际纸张和模板内容验收 |
| Android 单元测试 | 以 --rerun-tasks 实际重跑，3 项通过；主要为纯函数策略测试，不覆盖授权回调或真实后端失效 |
| Android lint | 0 错误／8 警告，通过 |

后端失败需要分别解释：`LabelServiceTest` 的 2 项仍断言三段二维码，而新实现返回五段码，应更新为新契约和兼容性断言；`ProcessDeliveryTest`、`QualityDeliveryTest`、`ShippingDeliveryTest` 的 JDBC 夹具未应用 V50，报 BATCH_CODE 列不存在，必须更新夹具后继续验证业务链。这 5 项不是上次缺探针问题的重复，也不能直接推断升级到 V55 后的实际数据库一定存在同样故障。

后端复跑命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot'
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml '-Dtest=OrderChangeServiceTest,OrderChangeFinanceTest,OrderChangeJdbcAtomicityTest,OrderChangeHttpTest,ProcessProgressServiceTest,PrepStockServiceTest,PrintJobServiceTest,JdbcPrintJobRepositoryTest,DatabaseUpgradeServiceTest,JdbcFinanceRepositoryTest,ProcessStationServiceTest,ProcessDeliveryTest,QualityRequirementServiceTest,QualityDeliveryTest,ShippingDeliveryTest,MotherOrderSplitServiceTest,LabelServiceTest,ScanContextHttpTest,ZhubaoErpApplicationTests' test
```

本机额外探针位于 `output/app-recheck/`，已移出默认源码和测试集合，API 全部模拟，没有修改业务数据。复跑：`pnpm --filter zhubao-erp-frontend exec vitest run --config F:/APPkaifa/zhubao-ERP/output/app-recheck/vitest.config.ts`。实际日志为 `.tmp-app-recheck-{frontend,types,backend,print-agent,android,android-unit,probes}.log`，生成物未纳入 Git。

正式 APK 仍依赖真实地址与仓库外签名材料；本次未重新制作发布包、未做真实 MySQL V49—V55 升级／恢复、未做手机和佳博验收。这些外部边界独立于上述仍需修改的代码问题。
