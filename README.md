# Minecraft Terracotta

Minecraft Terracotta 是一个基于 NeoForge 的 Minecraft 模组，旨在提供简单易用的多人联机解决方案。它集成了基于 Rust 编写的高性能 P2P 后端 (基于 EasyTier)，允许玩家在没有公网 IP 的情况下轻松创建和加入局域网房间。

## 📂 项目结构

本项目主要包含 Java Mod 代码：

*   **Java Mod (`src/main/java`)**:
    *   基于 NeoForge 加载器。
    *   负责游戏内 GUI (仪表盘、大厅、设置界面)。
    *   负责与外部 Terracotta 核心进程的通信 (HTTP API + Socket)。
    *   主要包路径: `com.multiplayer.terracotta`

## 🛠️ 构建指南

### 环境要求
*   **Java**: JDK 21 (推荐使用 IntelliJ IDEA)

### 构建步骤

1.  **克隆仓库**
    ```bash
    git clone https://github.com/YourUsername/MinecraftTerracotta.git
    cd MinecraftTerracotta
    ```

2.  **构建 Mod (Java)**
    Windows:
    ```powershell
    .\gradlew build
    ```
    Linux/macOS:
    ```bash
    ./gradlew build
    ```
    构建产物位于 `build/libs/` 目录。

> 注意：Terracotta 后端核心为独立项目，本仓库不再包含或维护其源码。模组将在运行时自动下载或使用用户在配置中指定的外部可执行文件。

## 📖 使用说明

1.  将构建好的模组 `.jar` 文件放入 Minecraft 的 `mods` 文件夹。
2.  启动游戏。
3.  在主界面点击 **Terracotta 多人联机** 或在游戏内按 `ESC` 菜单进入。
4.  **房主**: 点击“创建房间”，等待初始化完成后，将房间码分享给朋友。
5.  **玩家**: 点击“加入房间”，输入房间码即可连接。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 👏 鸣谢

*   **Terracotta Backend**: 本项目在设计上依赖后端核心项目 [burningtnt/Terracotta](https://github.com/burningtnt/Terracotta)，感谢原作者的开源贡献。

## 🌐 i18n / 本地化

本模组使用 Minecraft 原生语言文件系统进行本地化，语言文件位于：

* `src/main/resources/assets/minecraftterracotta/lang/en_us.json`
* `src/main/resources/assets/minecraftterracotta/lang/zh_cn.json`

界面文本（如仪表盘标题、按钮、状态提示等）通过语言键进行管理，例如：

* `terracotta.dashboard.title`
* `terracotta.host.title`
* `terracotta.state.host_starting`

如果你希望贡献新的语言翻译，可以：

1.  复制 `en_us.json` 为新的语言文件（例如 `xx_yy.json`）。
2.  按现有键值结构补全对应译文。
3.  提交 Pull Request。

## 📄 许可证

本项目采用 [GNU Affero General Public License v3.0](LICENSE) 许可证。
