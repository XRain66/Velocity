# Velocity
由XRain666翻译

[![构建状态](https://img.shields.io/github/actions/workflow/status/PaperMC/Velocity/gradle.yml)](https://papermc.io/downloads/velocity)
[![加入我们的 Discord](https://img.shields.io/discord/289587909051416579.svg?logo=discord&label=)](https://discord.gg/papermc)

Velocity 是一个具有无与伦比的服务器支持、可扩展性和灵活性的 Minecraft 服务器代理程序。

本项目基于 GPLv3 许可证开源。

## 重要更新

这是为RMS Server的定制版本，采用了正版登录和little skin登录双认证，下载后无需做任何事情，只需使`online-mode = true`便可使用！

都是万恶的重华老想着简洁（）

纳西妲最可爱啦！

## 项目目标

* 代码库易于上手，并始终遵循 Java 项目的最佳实践。
* 高性能：单个代理可以处理数千名玩家。
* 全新的、富有创新的 API，从零开始构建，既灵活又强大，同时避免其他代理软件的设计缺陷和次优设计。
* 为 Paper、Sponge、Fabric 和 Forge 提供一流的支持。（其他实现可能也能工作，但我们特别致力于支持这些服务器实现）

## 特色功能

* 双重验证支持：支持 Mojang 和 LittleSkin 验证，让玩家登录更加灵活
* 高性能设计：优化的网络架构，支持大量玩家同时在线
* 丰富的 API：为插件开发者提供强大的扩展能力
* 完善的错误处理：详细的日志记录，方便排查问题

## 构建说明

Velocity 使用 [Gradle](https://gradle.org) 构建。我们推荐使用包装脚本（`./gradlew`），因为我们的 CI 也使用它。

运行 `./gradlew build` 即可完成完整的构建周期。

## 运行说明

构建完成后，你可以从 `proxy/build/libs` 目录复制并运行带有 `-all` 后缀的 JAR 文件。Velocity 将生成一个默认配置文件，你可以根据需要进行配置。

或者，你也可以从[下载页面](https://papermc.io/downloads/velocity)获取代理服务器 JAR 文件。

## 配置要求

* Java 17 或更高版本
* 至少 512MB 内存（推荐 1GB 或更多）
* 支持的操作系统：Windows、Linux、macOS

## 快速开始

1. 下载最新的 Velocity JAR 文件
2. 使用以下命令启动服务器：
   ```bash
   java -jar velocity-proxy-xxx-all.jar
   ```
3. 编辑生成的 `velocity.toml` 配置文件
4. 重启服务器使配置生效

## 问题反馈

如果你在使用过程中遇到任何问题，可以：
* 在 GitHub 上提交 Issue
* 加入我们的 Discord 社区寻求帮助
* 查阅我们的[在线文档](https://docs.papermc.io/velocity)

## 贡献代码

我们欢迎各种形式的贡献，包括但不限于：
* 提交 Bug 报告
* 改进文档
* 提交功能请求
* 贡献代码

请确保在提交代码前阅读我们的贡献指南。

## 开源协议

Velocity 采用 GPLv3 协议开源。
