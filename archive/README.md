# 代码归档

这里保存已退出当前运行源码的原型和专属历史测试。当前功能以 [项目核心](../docs/00_项目核心.md) 和 `frontend/src/App.vue` 的页面消费链为准。

## 2026-10-13 归档

原目录结构保存在 `code/2026-10-13/`：

| 内容 | 原位置 | 归档依据 |
|---|---|---|
| 只读配置基线 | `backend/src/{main,test}/java/.../configuration` | 当前配置页使用账号、授权和存储接口，不消费旧基线服务 |
| 样例场景 API | `backend/src/{main,test}/java/.../sample` | 当前登记导入页使用 register／machine／business，不消费旧样例 API |
| 视频截取原型 | `backend/src/{main,test}/java/.../media` | 现行拍摄留证由 document 处理，没有页面或脚本调用旧截取 API |
| 两项旧管理 API 烟测 | 原 `M2ManagementApiSmokeTest` 的相应方法 | 只归档已退出接口的测试，保留现行管理 API 测试 |
| 二维码组件、旧配置数据和评审夹具 | `frontend/src/components/QrImage.vue`、`data/configurationBaselineData.ts`、`data/m1ReviewFixtures.ts`及专属测试 | 无运行时消费者；设备页有效测试改为最小本地夹具 |

完整文件可显式查询 `rg --files --no-ignore archive/code/2026-10-13`。归档不删除历史数据库迁移、客户数据、当前页面功能或必要底层依赖。

## 2026-10-14 归档

| 内容 | 原位置 | 归档依据 |
|---|---|---|
| 账号申请与审批面板及专属测试 | `frontend/src/features/m1/AccountApprovalPanel.vue`、`AccountApprovalPanel.test.ts` | `App.vue`、审批页、配置页、用户管理均不导入；配置页测试断言不展示该面板。用户管理直接保存账号。`authApi` 前置审批函数和后端兼容接口仍保留，本批不删接口、不删表 |
| 无页面调用的客户授权客户端及专属测试 | `frontend/src/services/customerAuthorizationApi.ts`、`customerAuthorizationApi.test.ts` | 现行用户管理通过账号保存写入客户范围，不导入该客户端。后端 `CustomerAuthorizationController`、服务、仓储和权限仍保留，不能把本批理解成客户授权功能已删除 |
| 旧扫码 resolve 的前端解析 | `frontend/src/features/scan/scanCode.ts` 中的 `parseScanCode` | 页面查单走 `/api/scan/context`。归档副本含当时的解析函数；现行文件只保留发货面板使用的 `extractCourierNumber`。后端 `POST /api/scan/resolve` 仍注册，本批不删接口 |
| 无调用的过程说明目标辅助 | `frontend/src/features/scan/progressTarget.ts` 中的 `progressTargetOf` | 三个现场面板使用 `progressTargetFromScan`。阶段标签仍留在现行文件 |
| 无调用的按设备读数客户端 | `frontend/src/services/deviceGatewayApi.ts` 中的 `fetchReadingsByDevice` | 设备页使用 `fetchRecentReadings`。后端按设备读数接口仍注册 |
| 无调用的登记覆盖色调包装 | `frontend/src/data/registerImportTemplates.ts` 中的 `registerCoverageTone` | 没有任何页面或测试调用；模板覆盖数据仍由现行文件内部计算 |

同时从 `frontend/src/styles.css`、`frontend/src/system-workspaces.css` 去掉只被该面板使用的 `.account-process-row`、`.account-row-actions`，以及没有任何页面使用的 `.account-admin-form`、`.account-admin-checks`。财务页仍使用 `.account-admin-grid`；配置页仍使用登录会话样式。

客户授权客户端归档时，另从 `system-workspaces.css` 去掉没有页面使用的 `.security-customer-auth`、`.security-account-contract`、`.customer-auth-form`、`.customer-auth-hint`、`.customer-auth-message`。配置页的登录身份和改密样式保留。本批不注销 `/api/security/customer-authorizations`。

## 使用边界

- 这里不在 Maven／Vite 的源码与测试根下，也不进入当前 Docker 构建上下文；旧接口不再注册。
- 默认搜索由根 `.ignore` 排除。只在追查历史时用 `rg --no-ignore <关键字> archive/code`。
- 文件按原路径保留便于定位，不代表可以直接恢复使用；恢复前重新评审当前需求、依赖、权限和验证。
- 文档历史在 [docs/archive](../docs/archive/README.md)，不与当前核心文档重复维护。
