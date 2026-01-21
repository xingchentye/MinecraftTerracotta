# 末影联机 (Minecraft Ender)

![Fabric](https://img.shields.io/badge/Fabric-1.21.x-006000?style=flat&logo=fabric)
![Forge](https://img.shields.io/badge/Forge-1.21.x-DFa86A?style=flat&logo=forge)
![NeoForge](https://img.shields.io/badge/NeoForge-1.21.x-DB6D18?style=flat&logo=neoforge)
![License](https://img.shields.io/badge/License-AGPL_3.0-blue.svg)

**末影联机 (Ender Online)** 是一个轻量级、跨平台的 Minecraft 多人联机模组，旨在为玩家提供简单、零配置的 P2P 联机体验。

本模组集成了高性能的 P2P 后端（基于 [EasyTier](https://github.com/EasyTier/EasyTier)），允许玩家在没有公网 IP 的情况下，通过房间码轻松创建和加入局域网世界。

## ✨ 主要特性

*   **全平台支持**：同时支持 **Fabric**、**Forge** 和 **NeoForge** 三大主流加载器（当前适配 Minecraft 1.21.x）。
*   **零配置联机**：无需端口映射，无需公网 IP，无需繁琐的服务器搭建。
*   **简单易用**：内置现代化的 GUI 仪表盘，一键创建/加入房间。
*   **房间管理**：房主可直接在游戏内管理房间权限（白名单、黑名单、访客权限等）。
*   **自动后端管理**：模组会自动下载并管理 P2P 后端进程，开箱即用。

## 📂 项目结构

本项目采用多模块架构，以支持不同 Mod 加载器：

*   **ender_core**: 核心逻辑与公共代码（独立于加载器）。
*   **fabric**: Fabric 版本的实现与适配。
*   **forge**: Forge 版本的实现与适配。
*   **neoforge**: NeoForge 版本的实现与适配。

## 🛠️ 构建指南

### 环境要求

*   **JDK**: 21 (推荐)
*   **Gradle**: 8.x (项目内置 gradlew)

### 构建命令

你可以选择构建特定平台的模组，或一次性构建所有版本。

**Windows (PowerShell):**
```powershell
# 构建所有版本
.\gradlew.bat build

# 仅构建 Fabric 版本
.\gradlew.bat :fabric:build

# 仅构建 Forge 版本
.\gradlew.bat :forge:build

# 仅构建 NeoForge 版本
.\gradlew.bat :neoforge:build
```

**Linux / macOS:**
```bash
# 构建所有版本
./gradlew build

# 仅构建 Fabric 版本
./gradlew :fabric:build

# 仅构建 Forge 版本
./gradlew :forge:build

# 仅构建 NeoForge 版本
./gradlew :neoforge:build
```

构建产物将生成在各子模块的 `build/libs/` 目录下。

## 📖 使用说明

1.  **安装模组**：下载对应加载器版本的 `.jar` 文件，放入 Minecraft 的 `mods` 文件夹。
2.  **启动游戏**：进入游戏主界面。
3.  **打开面板**：
    *   在主界面点击 **Ender 多人联机** 按钮。
    *   或者在游戏内按下快捷键（默认为 `K`，可在按键设置中修改）或通过 ESC 菜单进入。
4.  **创建房间 (房主)**：
    *   点击“创建房间”。
    *   等待后端初始化及连接 P2P 网络。
    *   复制生成的 **房间码** 发送给好友。
5.  **加入房间 (玩家)**：
    *   点击“加入房间”。
    *   输入好友分享的房间码。
    *   点击连接即可加入游戏。

## ⚙️ 配置与高级功能

*   **配置文件**：位于 `.minecraft/config/ender_online.json` (或类似路径)。
*   **自定义后端**：如果你处于特殊网络环境，可以在设置中指定自定义的 EasyTier 可执行文件路径或 P2P 节点 URL。

## 🤝 贡献与反馈

欢迎提交 Issue 反馈 Bug 或建议，也欢迎提交 Pull Request 参与开发。

*   **GitHub**: [MinecraftEnder](https://github.com/YourUsername/MinecraftEnder) (请替换为实际链接)

## 📄 许可证

本项目代码采用 **GNU Affero General Public License v3.0 (AGPL-3.0)** 许可证。

---
*Powered by EasyTier & EnderOnline Team*
