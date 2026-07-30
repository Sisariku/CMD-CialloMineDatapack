# CialloMine Datapack

一个为 Minecraft Fabric 1.21.1 添加电影视角功能与数据包可视化编辑器的模组。

## 功能

### 🎬 电影模式
- `/ciallo camera movie enable` 开启电影模式
- `/ciallo camera movie disable` 关闭电影模式
- 上下黑边遮幅
- 隐藏 HUD 和玩家的手
- `/ciallo head lock` 锁定头部
- `/ciallo head unlock` 解锁头部

### 📝 数据包编辑器（Ctrl+O 打开）
- 可视化文件树（支持展开/折叠/右键操作）
- 语法高亮编辑器（mcfunction 格式，支持中文 Unicode）
- 命令自动补全（Tab 切换 / Enter 确认 / 空格接受并加空格）
- 配方创建器（工作台 / 熔炉 / 高炉 / 烟熏炉 / 营火）
- 新建 / 重命名 / 删除 / 复制 / 粘贴文件
- 右键菜单操作

## 使用方法

1. 安装 Fabric Loader 和 Fabric API
2. 将模组放入 `mods` 文件夹
3. 在游戏内按 **O** 键打开数据包编辑器
4. 在数据包目录下编辑 mcfunction 文件

## 指令

| 指令 | 说明 |
|------|------|
| `/ciallo camera movie enable` | 开启电影模式 |
| `/ciallo camera movie disable` | 关闭电影模式 |
| `/ciallo head lock` | 锁定头部 |
| `/ciallo head unlock` | 解锁头部 |
| `/ciallo datapack edit` | 打开数据包编辑器 |
| `/ciallo datapack edit <路径>` | 打开指定路径的数据包编辑器 |

## 依赖

| 依赖 | 说明 |
|------|------|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Fabric 基础 API |
| [LDLib2](https://github.com/elbegast/LDLib-2) | UI 框架（ELBGG Fork） |

## 环境要求

- Java 21
- Gradle 8.x
- Minecraft 1.21.1
- Fabric Loader 0.16.7+

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/ciallominedatapack-1.0.0.jar`。

## 开发

AI 辅助开发，API：DeepSeek-V4 Pro。

## 许可证

MIT License
