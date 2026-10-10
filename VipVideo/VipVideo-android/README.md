# VIP Video Android

这是从现有桌面版迁移出的 Android 原生 WebView 版本，安装产物为 APK。它与 Electron 版共用 `VipVideo-electron/vlist.json`，包含以下手机端功能：

- 平台列表与分类标识
- 网页前进、后退和返回平台首页
- 支持的平台可选择播放线路
- 新窗口链接在当前窗口打开
- 视频全屏、网页媒体播放、文件下载
- 当前页面使用系统浏览器打开
- Android 6.0（API 23）及以上

## 最省事的构建方式：GitHub Actions

1. 将改动推送到 GitHub。
2. 打开仓库的 **Actions** 页面。
3. 选择 **Build Android APK**，点击 **Run workflow**。
4. 构建结束后，在页面底部下载 `VipVideo-android-debug`。
5. 解压后得到 `app-debug.apk`，传到安卓手机并允许“安装未知应用”即可安装。

这个 debug APK 已由 Android 调试证书签名，可以直接安装测试。正式发布应用商店时，应另建并妥善保管 release keystore，再生成签名 AAB/APK。

## 本地构建

要求：

- JDK 17
- Android SDK Platform 35 和对应 Build Tools
- Gradle 8.9

可用 Android Studio 打开本目录，等待同步完成后选择 **Build > Build APK(s)**；也可以在 PowerShell 中运行：

```powershell
cd VipVideo\VipVideo-android
.\build-android.ps1
```

生成文件：

```text
app\build\outputs\apk\debug\app-debug.apk
```

当前仓库所在电脑只有 Java 8 和 ADB，没有完整 Android SDK 与 Gradle，不能在这台机器上直接完成 APK 编译；使用上面的 GitHub Actions 不依赖本机环境。

## 配置与注意事项

站点和线路仍在 `../VipVideo-electron/vlist.json` 中维护，构建时会自动复制进 APK。部分视频网站可能会基于 DRM、登录策略、设备认证或 WebView 兼容性限制播放，这属于站点侧限制，不是 APK 打包错误。

请仅访问和播放你有权使用的内容，并遵守内容平台条款和当地法律。
