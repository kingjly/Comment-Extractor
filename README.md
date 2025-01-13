# 🕵️ Burp Comment Extractor - 注释提取与分析工具

## 🚀 项目状态

![Java](https://img.shields.io/badge/开发语言-Java-orange)
![Burp Suite](https://img.shields.io/badge/适用于-Burp%20Suite-blue)
![Version](https://img.shields.io/badge/版本-1.0-green)

## 📖 项目简介

Burp Comment Extractor 是一款专为 Burp Suite 打造的注释分析扩展工具。它能够自动从 Web 应用响应中提取各类注释信息，并智能识别其中可能包含的敏感信息，帮助安全测试人员快速发现潜在的安全隐患。

## ✨ 核心功能

- 🔍 **多类型注释提取**
  - HTML 注释自动提取
  - JavaScript 单行注释识别
  - JavaScript 多行注释分析

- 🚨 **智能信息检测**
  - 自动识别用户名密码信息
  - JWT Token 检测
  - API Key 发现
  - 隐藏 URL 扫描

- 📊 **便捷的操作界面**
  - 清晰的 URL 列表展示
  - 实时搜索过滤功能
  - 注释内容详细展示
  - 敏感信息突出显示

## 🛠️ 技术特性

- 基于 Java 开发
- 使用 Swing 构建图形界面
- 集成 Burp Suite 扩展 API
- 采用正则表达式进行精确匹配

## 🚀 使用指南

### 环境要求

- Burp Suite Professional/Community
- Java 运行环境 8 或更高版本

### 安装步骤

1. 直接下载 target 目录下的 comment-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar 文件 或使用 Maven 编译：mvn clean package
2. 打开 Burp Suite
3. 进入 `Extender`（扩展器）标签
4. 点击 `Add`（添加）按钮
5. 选择下载的 JAR 文件进行安装

### 使用方法

1. 安装完成后，切换到 "Comment Extractor" 标签页
2. 开始正常的 Web 应用测试流程
3. 工具会自动分析响应中的注释信息
4. 在左侧列表查看包含注释的 URL
5. 在右侧查看详细的注释内容和敏感信息分析

## 📝 功能说明

### 内容过滤
- 支持的内容类型：
  - text/html
  - text/xml
  - text/javascript
  - application/javascript
  - application/json
  - 等其他常见 Web 内容类型

### 敏感信息检测
- 自动识别以下类型的敏感信息：
  - 用户名密码组合
  - JWT 令牌
  - API 密钥
  - 内部 URL 地址

## ⚠️ 注意事项

1. 本工具仅供安全测试使用
2. 请在授权的范围内使用本工具
3. 注意保护测试过程中发现的敏感信息
4. 建议在测试环境中先行验证

## 📄 开源许可

本项目采用 MIT 许可证开源，详情请参阅 `LICENSE` 文件。

**免责声明**：本工具仅用于安全研究和授权测试目的，使用者需要遵守相关法律法规，并对使用过程中的行为负责。
