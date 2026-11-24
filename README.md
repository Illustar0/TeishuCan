# TeishuCan (Flutter)

[Illustar0/TeishuCan](https://github.com/Illustar0/TeishuCan) 的跨平台 Flutter 移植版本，支持 **iOS 与 Android**，底层通过 Dart FFI 绑定 [`Illustar0/libzzu`](https://github.com/Illustar0/libzzu) (`v0.2.0` C ABI 2.0) 访问郑州大学校园服务。

## 目录结构

- `packages/libzzu_ffi/`：本地 Flutter FFI 插件与原生桥接
  - `lib/src/`：Dart FFI 绑定（覆盖全部 38 个 C ABI 2.0 导出函数）、`NativeCallable.listener` 异步桥接、`ZzuRuntime`、领域客户端（`CasClient`、`EasClient`、`EcardClient`、`MailClient`、`PortalClient`、`SelfServiceClient`）、时区工具与异常模型
  - `ios/`：CocoaPods 配置（`libzzu_ffi.podspec`，`platform :ios, '15.0'`）、符号防裁剪引出文件（`libzzu_force_link.h/.c`）与 Swift 插件入口
  - `android/`：Kotlin 插件入口，读取 Android `AndroidCAStore` 系统根证书并传给 `zzu_runtime_new`
- `lib/domain/`：账号、课表、电费、校园服务、异常与主题设置等纯 Dart 领域模型
- `lib/data/`：会话状态管理、互斥锁、安全存储（`flutter_secure_storage`）、本地快照缓存与各业务仓库
- `lib/ui/`：圆角卡片与毛玻璃导航风格界面（首页、课表、服务、电费设置、外观设置、登录弹窗等）
- `lib/l10n/`：中文本地化（`zh`）与日文本地化（`ja`）文案
- `tool/fetch_libzzu.dart`：按目标平台下载并校验 SHA-256 的 `libzzu` v0.2.0 预编译库拉取脚本

## 环境准备

需要已安装 Flutter 稳定版（测试版本为 Flutter `3.50.1` / Dart `3.14.0`）。

1. 安装 Dart/Flutter 依赖：

   ```bash
   flutter pub get
   ```

2. 拉取目标平台所需的 `libzzu` v0.2.0 预编译动态库 / XCFramework（自动校验 SHA-256）：

   ```bash
   # 按需选择目标平台：ios | android | windows | linux | macos | all
   dart run tool/fetch_libzzu.dart --target windows
   ```

   各目标产物放置路径（已加入 `.gitignore`，不提交进仓库）：
   - `--target ios` -> `packages/libzzu_ffi/ios/Frameworks/libzzu.xcframework`
   - `--target android` -> `packages/libzzu_ffi/android/src/main/jniLibs/<abi>/libzzu.so`
   - `--target windows|linux|macos` -> `build/libzzu/<platform>/`

## 测试与构建

### 代码静态检查与单元测试

```bash
flutter analyze
dart analyze packages/libzzu_ffi tool/fetch_libzzu.dart
flutter test
```

### 运行原生 FFI 集成测试

先通过 `tool/fetch_libzzu.dart` 拉取当前宿主机平台库，再通过 `LIBZZU_LIBRARY_PATH` 指定动态库路径运行：

- Linux：

  ```bash
  dart run tool/fetch_libzzu.dart --target linux
  LIBZZU_LIBRARY_PATH="$PWD/build/libzzu/linux/libzzu.so" flutter test packages/libzzu_ffi/test/libzzu_ffi_test.dart
  ```

- Windows (PowerShell)：

  ```powershell
  dart run tool/fetch_libzzu.dart --target windows
  $env:LIBZZU_LIBRARY_PATH = "$PWD\build\libzzu\windows\zzu.dll"
  flutter test packages/libzzu_ffi/test/libzzu_ffi_test.dart
  ```

### 构建移动安装包

- **Android APK**：

  ```bash
  dart run tool/fetch_libzzu.dart --target android
  flutter build apk --release --split-per-abi
  ```

- **iOS 无签名包（需 macOS + Xcode）**：

  ```bash
  dart run tool/fetch_libzzu.dart --target ios
  flutter build ios --release --no-codesign
  ```

## 开源协议与致谢

- 本项目遵循上游仓库的 [GPL-3.0 协议](LICENSE)。
- 原始 Android 项目与界面设计参考自 [Illustar0/TeishuCan](https://github.com/Illustar0/TeishuCan)。
- 底层 Rust/C ABI 核心库来自 [Illustar0/libzzu](https://github.com/Illustar0/libzzu)。
