# 日常管理 · 安卓客户端

Kotlin + Jetpack Compose 原生 App，通过 HTTP 连一个自建服务端取数据（`/api/v1`）。
配合的服务端见 [checkin-site-public](https://github.com/Lele355/checkin-site-public)。

> **这是从私人仓库导出的脱敏版本**：域名替换成 `example.com`，签名密钥、`local.properties`
> 与实际部署信息均不包含。
>
> 包名仍是 `cn.anlele.checkin`。这是 App 的身份，改了就没法覆盖升级，备案也得重做，
> 所以保留原样；它不是"个人信息"，只是一个包名。

## 功能

- 工作台：问候卡 + 今日打卡/课程/日程/备忘/纪念日/记账等卡片
- 日程：列表分组（今天 / 明天 / 一周内 / 更远 / 已过）+ 月历双视图
- 备忘、习惯打卡、课程表（周次 + 图例 + 彩色块）、记账（三栏汇总 + 分类占比）、统计（热力图 + 排行 + 月柱）
- 纪念日、留言板、情侣空间、周期助手、提醒中心、AI 助手、邮件、我的
- **本地通知**：日程到点、每日早报、打卡提醒、纪念日 —— 全部用 `AlarmManager` 在本机定时，
  不接任何第三方推送 SDK（见下方[通知](#通知)）

## 技术栈

| 层 | 选型 |
| --- | --- |
| UI | Jetpack Compose（Material3 BOM 2025.08.00）+ 自绘组件层 |
| 网络 | OkHttp + kotlinx.serialization |
| 本地存储 | DataStore Preferences（令牌 / 离线快照 / 主题） |
| 认证 | Bearer 令牌（服务端只存 SHA-256） |
| 通知 | AlarmManager + BroadcastReceiver + NotificationChannel |
| 构建 | AGP 8.13 / Kotlin 2.4 / compileSdk 36 / minSdk 24 |

## 构建

需要 JDK 21 与 Android SDK（默认找 `D:/Android/Sdk`，可用 `local.properties` 覆盖）。

```bash
./build_apk.sh debug      # 或 release
# 产物：app/build/outputs/apk/<debug|release>/app-<type>.apk
```

只做编译自检（比打包快得多，改完代码先跑这个）：

```bash
JAVA_HOME=/d/jdk21 ./gradlew :app:compileDebugKotlin
```

- debug 包默认连 `http://10.0.2.2:5199`（安卓模拟器里的宿主机），需本地起服务在 5199
- release 包连 `https://example.com/api/v1`

## 接口地址

地址定义在 `app/build.gradle.kts` 的 `API_BASE`，按 buildType 分成两份：

| 构建类型 | 默认值 | 说明 |
| --- | --- | --- |
| debug | `http://10.0.2.2:5199` | 安卓模拟器访问宿主机的固定地址；实体机调试改成你电脑的局域网 IP |
| release | `https://example.com/api/v1` | **脱敏占位值**，跑之前必须改成你自己的服务端 |

Debug 包允许明文 HTTP，靠的是 `src/debug/AndroidManifest.xml` 里的
`usesCleartextTraffic`；release 包没有这个开关，必须走 HTTPS。

服务端要自己起一个——见 [服务端仓库](https://github.com/Lele355/checkin-site-public)。
后端接口不在这个仓库里，App 只是个客户端。

## 通知

全部通知都在**本机定时**，用 `AlarmManager.setAlarmClock()` 触发，
不接任何第三方推送 SDK，因此不需要联网也能响。代价是必须手动放行系统权限：

| 权限 | 不放开会怎样 | 怎么开 |
| --- | --- | --- |
| 通知（Android 13+） | 到点了什么都看不到 | 设置 → 应用 → 通知 → 允许 |
| 闹钟和提醒 | 时间会漂，Doze 状态下不响 | 设置 → 应用 → 特殊权限 → 闹钟和提醒 |
| 电池优化「无限制」 | 后台被冻结，闹钟被系统清掉 | 设置 → 应用 → 电池 → 无限制 |
| 厂商自启动 | 重启或长时间不用后不再触发 | 各家私有开关，见下 |

「我的 → 通知」页里有**权限体检卡**，进页面会自动刷新四项状态；
还有**闹钟自检**（5 秒后响一次），排查链路最快的手段——自检不响就别怀疑业务逻辑了，
先看权限。

厂商自启动没有统一接口，App **无法检测**，只能手动找：

- 小米：设置 → 应用设置 → 应用管理 → 找到本应用 → 自启动
- 华为/荣耀：设置 → 应用 → 应用启动管理 → 关掉「自动管理」→ 允许自启动、关联启动、后台活动
- OPPO/一加：设置 → 电池 → 应用耗电管理 → 允许后台运行
- vivo：设置 → 电池 → 后台耗电管理 → 允许后台高耗电

Android 本身并没有「自启动权限」这个东西，只有 `RECEIVE_BOOT_COMPLETED` 这个广播；
上面那些开关都是厂商自己加的。

## 常见问题

**编译报 compileSdk 不够**
Compose BOM 必须是 `2025.08.00`。写成更新的版本会要求 compileSdk 37 + AGP 9.1，
当前 AGP 8.13 直接编不过。

**登录一直转圈**
先确认 debug 包的地址对不对。模拟器要用 `10.0.2.2` 而不是 `localhost`，
后者指向模拟器自己。

**改了界面但跑起来没变**
`./gradlew :app:clean :app:assembleDebug`。偶发是增量编译没接住 Compose 的重组。

**侧栏点了没反应**
路由名是单数（`expense` 不是 `expenses`），写错会静默落到默认页，看起来像"点了没反应"。

## 目录

| 路径 | 作用 |
|---|---|
| `ui/screens/` | 每个功能一个 Screen（工作台 / 日程 / 课表 / 记账 / AI …） |
| `ui/components/` | 站点同款组件：`SiteKit`（卡片/页头/列表行）、`DatePicker`（日期/时间/数字/周次）、`EditSheet`（底部弹层表单） |
| `ui/theme/` | 5 套主题的 `Palette`，字段名与网页 `tokens.css` 一一对应 |
| `ui/shell/` | `MainShell`（侧栏 + 顶栏 + 路由）、`PrefsSheet`（外观设置） |
| `data/` | `Api`（HTTP + 超时）、`Repo`（业务封装）、`Session`（Token）、`Models`（DTO） |
| `push/` | 本地通知：`AlarmScheduler` / `NotifyPlan` / `PermCheck` |
| `util/` | `Dates`（日期计算）、`Weeks`（课程周次解析） |

## 改代码的几条硬规矩

1. 新页面一律用 `ui/components` 里的现成组件，不要在页面里手搓卡片和页头
2. 日期/时间/数字/周次一律用 `DateField` / `TimeField` / `NumberField` / `WeekField`，
   不要用 `AppField` 让用户手打
3. 颜色与圆角只从 `LocalPalette` 取，不许写死
4. Material3 的 BOM 只能用 `2025.08.00`（更新版要求 compileSdk 37 + AGP 9.1，当前编不过）
5. 删除类操作不要弹 `AlertDialog` 二次确认，用 `SheetDelete`（弹层内一次点击 + 服务端确认语义）

## 发版

自己发版时注意版本号要**三处同步**，缺一处就会出现「设置页显示旧版本 / 更新提示不出现」：

1. `app/build.gradle.kts` 的 `versionCode`（+1）与 `versionName`
2. 服务端的 `appsettings.json`：`App:ClientVersion` / `App:ClientVersionCode` / `App:ClientNotes`
3. 打好的 `app-release.apk` 放到服务端的静态下载目录（网站下载页直接提供这个文件）

签名用 `keystore/release.jks`，密码走 `keystore.properties`（两者都不在仓库里）。
换签名 = 用户必须卸载重装，所以密钥要长期保管。

## 许可

MIT，见 [LICENSE](LICENSE)。签名文件、备案信息与实际部署配置不随代码授权。
