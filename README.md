# CialloMine Datapack

一个为 Minecraft Fabric 1.21.1 添加电影视角功能与数据包可视化编辑器的模组。

## 功能

🎬 指令一键切换电影视角模式
⬛ 上下黑边遮幅
📦 隐藏 HUD 和玩家的手
📝 可视化数据包编辑器
🔍 mcfunction 语法高亮与命令补全
📋 可视化配方创建器

## 使用方法

1. 安装 Fabric Loader 和 Fabric API
2. 将模组放入 mods 文件夹
3. 按 O 键打开数据包编辑器

## 指令

| 指令 | 说明 |
|------|------|
| `/ciallo camera movie enable` | 开启电影模式 |
| `/ciallo camera movie disable` | 关闭电影模式 |
| `/ciallo head lock` | 锁定头部 |
| `/ciallo head unlock` | 解锁头部 |
| `/ciallo datapack edit` | 打开数据包编辑器 |

## 依赖

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [LDLib2](https://github.com/elbegast/LDLib-2)（ELBGG Fork）

## 开发

AI 辅助开发。API：DeepSeek-V4 Pro

## 环境要求

- Java 21
- Gradle 8.x
- Minecraft 1.21.1
- Fabric Loader 0.16.7+

## 构建

```bash
./gradlew build
```

## 许可证

MIT License
