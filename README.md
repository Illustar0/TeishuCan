<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="96" alt="TeishuCan 图标" />
  <h1>TeishuCan</h1>
  <p>一个开源、免费、面向郑州大学学生的 Android 校园生活助手。</p>

  [![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
  [![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)
  [![CI](https://github.com/Illustar0/TeishuCan/actions/workflows/ci.yml/badge.svg)](https://github.com/Illustar0/TeishuCan/actions/workflows/ci.yml)

  [下载应用](https://github.com/Illustar0/TeishuCan/releases) · [开发构建](https://github.com/Illustar0/TeishuCan/actions) · [问题反馈](https://github.com/Illustar0/TeishuCan/issues)
</div>

## 简介

TeishuCan 将分散在不同校园平台中的日常功能集中到同一个应用中，让你更方便地查看课程安排、校园卡余额和宿舍剩余电量，并快速访问教务、图书馆等校园服务。

应用采用 Kotlin 和 Jetpack Compose 开发，使用 Miuix 构建界面，并支持多种个性化主题与交互效果。

## 功能

| 模块 | 功能 |
| --- | --- |
| 首页 | 查看下一节课、个人信息、校园卡余额及已添加电表的剩余电量 |
| 课表 | 同步教务课表，按学期和周次查看课程，查看上课时间、地点、教师及冲突课程 |
| 校园卡 | 查询余额、刷新校园卡信息、发起校园卡充值（实验性功能） |
| 宿舍电费 | 添加、编辑和管理多个电表，查询剩余电量并发起充值 |
| 校园服务 | 分类浏览、搜索校园服务，快速进入相关网页系统 |
| 个性化设置 | 中日双语、浅色与深色模式、主题色、Monet 配色、模糊、悬浮导航栏、液态玻璃及预测性返回（部分效果取决于系统版本） |

### 校园服务

登录后可从应用内进入以下校园服务；这些入口主要通过学校的单点登录与网页系统提供，而非全部由应用独立实现。

- **教务：** 学籍信息、培养方案、学业预警、考试安排、课程成绩、课表、全校开课、校历、空闲教室、教室课表。
- **图书馆：** 图书馆门户、馆藏检索与借阅、座位与研讨间预约。
- **学生事务：** 一卡通、校园邮箱、请假、校长信箱。

## 下载与使用

### 安装

TeishuCan 最低支持 **Android 10（API 29）**。

正式版本发布后，可以在 [GitHub Releases](https://github.com/Illustar0/TeishuCan/releases) 中下载 APK；如果暂时没有正式版本，可查看 [GitHub Actions](https://github.com/Illustar0/TeishuCan/actions) 中成功构建且提供 APK 的运行记录，或自行编译。

| APK 类型 | 适用设备 |
| --- | --- |
| `arm64-v8a` | 大多数现代 Android 手机和平板 |
| `armeabi-v7a` | 32 位 ARM 设备 |
| `x86_64` / `x86` | 对应架构的设备或模拟器 |
| `universal` | 不确定设备架构时使用 |

### 开始使用

1. 安装并打开 TeishuCan。
2. 使用自己的郑州大学校园账户登录；如果学校要求短信验证，按提示完成验证。
3. 登录成功后即可查看首页信息、同步课表及访问校园服务。
4. 如需查询宿舍电量，在「设置 → 电表」中按实际校区、楼栋、楼层及房间添加电表。

应用支持下拉刷新，并在进入前台时按数据有效期自动刷新。已有缓存数据可在网络不佳时供浏览，但余额、电量等实时信息仍应以学校系统为准。

> [!IMPORTANT]
> 校园卡充值目前为实验性功能。涉及支付时，请仔细核对金额与支付状态；若结果不明确，请先查看官方系统中的余额和交易记录，不要直接重复支付。

## 界面与体验

- 基于 Jetpack Compose 和 Miuix 的原生 Android 界面。
- 支持手机及宽屏设备的自适应导航布局。
- 提供明暗主题、动态配色及多种视觉效果。
- 支持中文、日语以及跟随系统语言设置。
- 对旧系统不支持的特效进行降级或隐藏。

## 技术栈

| 技术 | 用途 |
| --- | --- |
| [Kotlin](https://kotlinlang.org/) | 应用主要开发语言 |
| [Jetpack Compose](https://developer.android.com/compose) / Miuix | 界面、主题和交互组件 |
| [Hilt](https://dagger.dev/hilt/) | 依赖注入 |
| Kotlin Coroutines / Flow | 异步任务、状态管理与数据刷新 |
| AndroidX ViewModel / DataStore | 界面状态与本地持久化 |
| `com.illustar0:zzu` | 郑州大学校园服务客户端 |
| Firebase Analytics / Crashlytics | 使用统计及崩溃诊断 |
| Detekt / JUnit / Baseline Profile | 静态检查、单元测试和启动性能优化 |

## 本地开发

### 环境要求

根据当前仓库的构建配置，需要：

- Android Studio（支持项目所用 Android Gradle Plugin 与 SDK 版本）；
- Android SDK 37，包含编译配置所需的 37.2 minor API；
- JDK 25（项目 Gradle Daemon 与 CI 使用的版本，应用 Java 目标兼容级别为 21）；
- Git 与 [`git-cliff`](https://git-cliff.org/)，后者必须加入 `PATH`；
- 可访问 Google Maven、Maven Central 和 Gradle 分发服务的网络环境。

项目已经提供 Gradle Wrapper，无需单独安装 Gradle。构建过程使用 Git 提交历史及 tag 计算版本号，因此建议使用完整克隆，而不是下载源码 ZIP 或进行浅克隆。

### 构建

```bash
git clone https://github.com/Illustar0/TeishuCan.git
cd TeishuCan
./gradlew :app:assembleDebug
```

Windows PowerShell 下执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建产物通常位于 `app/build/outputs/apk/debug/`。也可以使用 Android Studio 打开项目根目录，完成 Gradle 同步后，选择 Android 设备或模拟器运行 `app` 模块。

若需构建 Release APK：

```bash
./gradlew :app:assembleRelease
```

未提供发布签名配置的本地 Release 构建不会使用官方发布签名。个人开发和调试建议使用 Debug 构建。

### 检查与测试

以下命令与项目 CI 中的常规检查保持一致：

```bash
./gradlew --continue :app:detekt :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

## 项目结构

```text
TeishuCan/
├── app/                          # Android 应用模块
│   └── src/
│       ├── main/java/com/illustar0/teishucan/
│       │   ├── concurrency/      # 并发刷新与任务状态
│       │   ├── data/             # 数据仓库、会话、本地缓存
│       │   ├── domain/           # 领域数据模型
│       │   ├── di/               # 依赖注入配置
│       │   ├── memory/           # 内存管理
│       │   ├── platform/         # 系统版本与能力适配
│       │   └── ui/               # 首页、课表、服务、设置和通用组件
│       ├── main/res/             # 应用资源与中日文翻译
│       └── test/                # 单元测试
├── baselineprofile/              # Baseline Profile 生成模块
├── config/detekt/                # 静态代码检查配置
├── gradle/                      # 版本目录与 Gradle Wrapper
├── .github/workflows/            # CI、APK 打包及发布流程
└── third_party/                 # 第三方资源与许可证
```

## 隐私与说明

- 登录会话保存在由 Android KeyStore 保护的本地加密文件中，并排除在系统备份之外。退出登录会清除相应的本地凭据。
- 应用集成 Firebase Analytics 和 Crashlytics，用于使用统计与崩溃诊断；广告 ID 收集及广告相关权限已禁用。
- TeishuCan 是独立开发的非官方校园助手，并非郑州大学官方客户端。功能可用性依赖学校相关系统及其服务状态。
- 校园账户、教务数据、支付等事项请以学校官方平台为准。

## 贡献与反馈

欢迎通过 [Issues](https://github.com/Illustar0/TeishuCan/issues) 提交 Bug、功能建议或使用反馈，也欢迎提交 Pull Request。

报告问题时，建议注明应用版本、Android 系统版本、设备型号、复现步骤及预期结果。请勿在公开问题中提交校园账号、密码、验证码或其他个人敏感信息。

## 开源协议

本项目使用 [GNU General Public License v3.0](LICENSE)（GPL-3.0）开源许可证。

项目使用的第三方库及资源遵循各自的开源协议。可在应用「设置 → 关于 → 开放源代码许可」中查看相关信息；Lucide 图标许可见 [`third_party/lucide/LICENSE`](third_party/lucide/LICENSE)。
