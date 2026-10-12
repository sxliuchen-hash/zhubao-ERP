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

## 使用边界

- 这里不在 Maven／Vite 的源码与测试根下，也不进入当前 Docker 构建上下文；旧接口不再注册。
- 默认搜索由根 `.ignore` 排除。只在追查历史时用 `rg --no-ignore <关键字> archive/code`。
- 文件按原路径保留便于定位，不代表可以直接恢复使用；恢复前重新评审当前需求、依赖、权限和验证。
- 文档历史在 [docs/archive](../docs/archive/README.md)，不与当前核心文档重复维护。
