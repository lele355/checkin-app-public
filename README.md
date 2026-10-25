# 日常管理 App（脱敏开源版）

个人自用的生活管理 App —— 原生 Android 客户端，Kotlin + Jetpack Compose 全本地渲染。
**这是从私人仓库导出的脱敏版本**：域名已替换为 `example.com`，签名密钥与本地配置均未包含。

## 功能

- 工作台：问候卡 + 权限标签 + 今日打卡/课程/日程/备忘/纪念日/记账/情侣/经期等卡片
- 日程：列表分组（今天/明天/一周内/更远/已过）+ 月历双视图
- 备忘、习惯打卡、课程表（周次 + 图例 + 彩色块）、记账（三栏汇总 + 分类占比）、统计（热力图 + 排行 + 月柱）
- 纪念日、留言板、情侣空间、经期助手、提醒中心、AI 助手、邮件、我的
- **本地通知**：日程到点、每日早报、打卡提醒、纪念日、经期预测
  —— 全部用 `AlarmManager.setAlarmClock()` 在本机定时，**不接任何第三方推送 SDK**

## 技术栈

| 层 | 选型 |
|---|---|
| UI | Jetpack Compose（Material3）+ 自绘组件层 |
| 网络 | OkHttp + kotlinx.serialization |
| 本地存储 | DataStore Preferences（令牌 / 离线快照 / 主题） |
| 认证 | Bearer 令牌（服务端只存 SHA-256） |
| 通知 | AlarmManager + BroadcastReceiver + NotificationChannel |
| 构建 | AGP 8.13 / Kotlin 2.4 / compileSdk 36 / minSdk 24 |

## 目录

```
app/src/main/java/cn/anlele/checkin/
├─ data/          接口封装、数据模型、会话存储
├─ push/          闹钟排程、通知、权限体检、重排广播
├─ ui/
│  ├─ components/ 通用组件（SiteKit 页面骨架、Switch、TimePicker…）
│  ├─ screens/    各业务页面
│  ├─ shell/      自研路由栈与侧栏
│  └─ theme/      五套主题的调色板与尺寸令牌
└─ util/          日期与格式化工具
```

## 怎么跑起来

1. 你需要一个兼容的后端（本项目配套的服务端未包含在公开版里）。
2. 把 `app/build.gradle.kts` 里的 `API_BASE` 改成你自己的地址：
   `buildConfigField("String", "API_BASE", "\"https://example.com/api/v1\"")`
3. 直接 `./gradlew assembleDebug` 出调试包。
4. 要出正式签名包：在项目根目录放 `keystore.properties`（storeFile / storePassword / keyAlias / keyPassword），
   构建脚本检测到才会启用 release 签名，没有就自动跳过 —— 所以这份代码不含任何密钥也能编译。

## 关于包名

包名沿用实际发布时的 `cn.anlele.checkin`（已做 App 备案），不做替换 —— 改包名会让全部源码的 package 声明与目录结构失真，
反而看不出真实工程长什么样。代码本身不含任何密钥或个人数据。

## 关于包名

包名沿用实际发布时的 `cn.anlele.checkin`（已做 App 备案），不做替换 —— 改包名会让全部源码的 package 声明与目录结构失真，
反而看不出真实工程长什么样。代码本身不含任何密钥或个人数据。

## 许可

MIT。
