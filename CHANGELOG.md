# Changelog

所有重要的变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [0.0.10] - 2025-12-29

### 🔧 改进

- **JsonHttpLogFormatter 优化**：当 `normalized_url` 与 `url` 一致时，不写入 `normalized_url` 字段，避免冗余信息，减少日志体积

---

## [0.0.9] - 2025-12-29

### ✨ 新增功能

#### Spring Boot 自动配置增强
- **新增 `includeEvents` 配置项**：支持通过配置文件控制是否包含完整的事件序列
  ```yaml
  mc:
    http:
      logging:
        include-events: true  # 开启后会在 timing.events 中记录所有事件的详细信息
  ```
- **提取 `HttpLoggingProperties` 为独立类**：提升配置属性的 IDE 识别度
- **添加 `spring-boot-configuration-processor` 支持**：自动生成配置元数据，支持 IDE 自动补全和文档提示

### 🔧 改进

- 改进 Spring Boot 配置属性的 IDE 支持，提供更好的开发体验

---

## [0.0.8] - 2025-12-29

### ✨ 新增功能

#### URL 规范化支持
- **新增 `UrlNormalizer` 工具类**：支持将 URL 路径中的数字 ID 替换为占位符，降低监控系统中的标签基数
  - 默认使用 `{id}` 占位符替换所有数字 ID
  - 支持自定义占位符（如 `{showId}`, `{ticketId}`），按顺序匹配 URL 中的数字 ID
  - 如果占位符数量少于数字数量，超出部分使用默认 `{id}` 占位符
  - 如果占位符数量多于数字数量，多余的占位符会被忽略

#### HttpLogContext 增强
- **新增 `urlPlaceholders` 字段**：支持在上下文中配置 URL 占位符，用于自定义 URL 规范化规则
  - 通过 `HttpLogContext.builder().urlPlaceholders("{showId}", "{ticketId}")` 配置
  - 占位符配置会自动传递到格式化器和监控上报中

#### HttpLogData 增强
- **新增 `getNormalizedRequestUrl()` 方法**：便捷获取规范化后的请求 URL
  - 自动从 `HttpLogContext` 中读取占位符配置
  - 如果未配置占位符，使用默认 `{id}` 占位符
  - 返回的 URL 已移除 query 参数，仅保留路径部分

#### 格式化器增强
- **JsonHttpLogFormatter**：
  - 新增 `normalized_url` 字段，显示规范化后的 URL
- **TextHttpLogFormatter**：
  - 当规范化 URL 与原始 URL 不同时，显示额外的 "Normalized URL" 行

### 📝 文档

- 更新 `HttpLogContext` 和 `RequestContext` 的 JavaDoc，添加 URL 规范化使用示例
- 更新 `HttpLogData.getNormalizedRequestUrl()` 的 JavaDoc，提供详细的行为说明和使用示例

---

## [0.0.6] - 2025-12-25

### 🔧 改进

- **默认日志长度设置为无限**：将默认记录的请求体/响应体最大长度设置为无限制（`-1`），确保完整记录所有内容

---

## [0.0.3] - 2025-12-25

### ✨ 新增功能

- **HttpLogData.Request 新增 `urlWithoutQuery` 字段**：提供移除 query 参数后的 URL，便于在监控和日志中使用更简洁的 URL 标识

---

## [0.0.2] - 2025-12-04

### ✨ 新增功能

#### 格式化器增强
- **JsonHttpLogFormatter 新增 `includeEvents` 支持**：支持记录完整的事件序列，便于分析调用链路
  - 开启后会在 `timing.events` 中记录所有事件的详细信息
  - 可通过 `setIncludeEvents(true)` 方法启用

#### 异常处理增强
- **新增 `HttpLoggingExceptionHandler`**：提供统一的异常记录接口
  - 支持在全局异常处理器中调用 `HttpLoggingExceptionHandler.record(e)` 记录异常
  - 在不同提供器（客户端和服务端）中都支持异常输出

### 🔧 改进

- **默认格式化器改为 JSON**：提升日志的结构化程度，便于 ELK 等日志分析系统处理

---
