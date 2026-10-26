# 贡献说明

这是个人自用的生活管理 App 的 Android 客户端（Kotlin + Jetpack Compose），
仓库公开主要是留档与参考，不是在做产品。

- **Issue 欢迎**：崩溃、UI 错位、"这段为什么这么写"都看。
- **PR 谨慎接受**：功能方向按个人需求长，大改动请先开 Issue 聊。
- **不接受商用模板化**。

## 本地跑

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
./gradlew :app:assembleDebug
```

`local.properties` 里补上 `sdk.dir=/path/to/Android/Sdk`（已在 `.gitignore` 里）。

**接口地址要自己配**：仓库里默认指向 `https://example.com/api/v1`，那是脱敏占位值，
跑之前请改成你自己的服务端。位置在 `app/build.gradle.kts` 的 `API_BASE`。

Debug 包走明文 HTTP，只在 `src/debug/AndroidManifest.xml` 里开了
`usesCleartextTraffic`；Release 包必须 HTTPS。

## 几条硬规矩

1. **Compose BOM 锁在 2025.08.00**。更新的版本要求 compileSdk 37 + AGP 9.1，
   和当前的 AGP 8.13 编不过。想升就得一起升，别只改一行。
2. **新页面用 `ui/components/SiteKit.kt` 那套**（`PageHeadCard` / `SiteRow` / `GroupHead`…），
   不要在页面里手搓卡片和标题，否则五套主题下会各不相同。
3. **颜色一律取主题令牌**（`ui/theme/` 的 `p.xxx`），不要写死色值。`c(hex, alpha)`
   的 alpha 默认留 -1，让 hex 自带透明度生效。
4. **日期用 `DateField` / `TimeField` / `WeekField`**，不要手打字符串。
5. **删除操作用 `SheetDelete`**（就地二次确认），不要弹 Dialog 拦截流程。

## 提交前

```bash
./gradlew :app:compileDebugKotlin
```

顺手在模拟器或真机过一遍改动的那页——Compose 的某些问题在软件渲染和真机上表现不同。

## 不要提交的东西

`keystore/`、`keystore.properties`、`local.properties`、`build/`、`.gradle/`、`*.apk`。