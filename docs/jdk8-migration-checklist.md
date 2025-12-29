# JDK 1.8 支持迁移清单

本文档列出了在 JDK 1.8 分支中需要特殊处理的内容。

**注意：** 主分支（JDK 21）中的语法特性（instanceof 模式匹配、switch 表达式、var 关键字）已经全部改为兼容 JDK 1.8 的写法，因此这些语法修改**不需要**在 JDK 1.8 分支中重复进行。

## 📋 需要在 JDK 1.8 分支中特殊处理的内容

### 1. pom.xml 配置修改

#### 1.1 Java 版本配置
```xml
<!-- 修改前 -->
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>

<!-- 修改后 -->
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
```

#### 1.2 依赖版本降级

| 依赖 | JDK 21 版本 | JDK 1.8 版本 | 说明 |
|------|------------|-------------|------|
| Lombok | 1.18.42 | 1.18.30 | 使用支持 JDK 1.8 的最新版本 |
| SLF4J | 2.0.17 | 1.7.36 | SLF4J 2.x 需要 Java 11+ |
| Fastjson2 | 2.0.60 | 2.0.60 | 保持，Fastjson2 支持 JDK 1.8 |
| OkHttp | 5.3.0 | 5.3.0 | OkHttp 5.3.0 支持 Java 8+，无需降级 |
| Spring | 6.2.14 | 5.3.31 | Spring 6.x 需要 Java 17+ |
| Spring Boot | 3.5.8 | 2.7.18 | Spring Boot 3.x 需要 Java 17+ |
| Servlet API | 6.1.0 (Jakarta) | 4.0.1 (javax) | Jakarta EE 9+ 需要 Java 11+ |
| JUnit | 5.11.4 | 5.10.2 | 使用支持 JDK 1.8 的版本 |
| Mockito | 5.14.2 | 4.11.0 | Mockito 5.x 需要 Java 11+ |
| Logback | 1.5.21 | 1.2.12 | Logback 1.5.x 需要 Java 11+ |

**注意：** 
- Spring Boot 2.7.x 是最后一个支持 JDK 1.8 的版本
- OkHttp 4.12.0 是最后一个支持 JDK 1.8 的版本
- SLF4J 1.7.36 是最后一个支持 JDK 1.8 的版本

#### 1.3 pom.xml 具体修改位置

**修改依赖版本变量：**
```xml
<properties>
    <!-- ... -->
    <lombok.version>1.18.30</lombok.version>
    <slf4j.version>1.7.36</slf4j.version>
    <okhttp.version>4.12.0</okhttp.version>
    <spring.version>5.3.31</spring.version>
    <spring-boot.version>2.7.18</spring-boot.version>
    <servlet.version>4.0.1</servlet.version>
    <junit.version>5.10.2</junit.version>
</properties>
```

**修改 Servlet API 依赖：**
```xml
<!-- 修改前 -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>${servlet.version}</version>
    <optional>true</optional>
    <scope>provided</scope>
</dependency>

<!-- 修改后 -->
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <version>${servlet.version}</version>
    <optional>true</optional>
    <scope>provided</scope>
</dependency>
```

**修改测试依赖中的 Logback：**
```xml
<!-- 修改前 -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.21</version>
    <scope>test</scope>
</dependency>

<!-- 修改后 -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.2.12</version>
    <scope>test</scope>
</dependency>
```

### 2. 代码修改：Jakarta Servlet → javax.servlet

需要将所有 `jakarta.servlet` 包名替换为 `javax.servlet`。

**需要修改的文件（共 6 个）：**

1. **`src/main/java/io/github/http/log/snap/server/spring/HttpLoggingFilter.java`**
   - `jakarta.servlet.FilterChain` → `javax.servlet.FilterChain`
   - `jakarta.servlet.ServletException` → `javax.servlet.ServletException`
   - `jakarta.servlet.http.HttpServletRequest` → `javax.servlet.http.HttpServletRequest`
   - `jakarta.servlet.http.HttpServletResponse` → `javax.servlet.http.HttpServletResponse`

2. **`src/main/java/io/github/http/log/snap/server/spring/HttpLoggingHandlerInterceptor.java`**
   - `jakarta.servlet.http.HttpServletRequest` → `javax.servlet.http.HttpServletRequest`
   - `jakarta.servlet.http.HttpServletResponse` → `javax.servlet.http.HttpServletResponse`

3. **`src/main/java/io/github/http/log/snap/server/spring/HttpLoggingAutoConfiguration.java`**
   - `jakarta.servlet.Filter` → `javax.servlet.Filter`

4. **`src/main/java/io/github/http/log/snap/server/spring/CachedBodyHttpServletResponse.java`**
   - `jakarta.servlet.ServletOutputStream` → `javax.servlet.ServletOutputStream`
   - `jakarta.servlet.WriteListener` → `javax.servlet.WriteListener`
   - `jakarta.servlet.http.HttpServletResponse` → `javax.servlet.http.HttpServletResponse`
   - `jakarta.servlet.http.HttpServletResponseWrapper` → `javax.servlet.http.HttpServletResponseWrapper`

5. **`src/main/java/io/github/http/log/snap/server/spring/HttpLogCustomizer.java`**
   - `jakarta.servlet.http.HttpServletRequest` → `javax.servlet.http.HttpServletRequest`

6. **`src/main/java/io/github/http/log/snap/server/spring/CachedBodyHttpServletRequest.java`**
   - `jakarta.servlet.ReadListener` → `javax.servlet.ReadListener`
   - `jakarta.servlet.ServletInputStream` → `javax.servlet.ServletInputStream`
   - `jakarta.servlet.http.HttpServletRequest` → `javax.servlet.http.HttpServletRequest`
   - `jakarta.servlet.http.HttpServletRequestWrapper` → `javax.servlet.http.HttpServletRequestWrapper`

**批量替换方法：**
```bash
# 使用 IDE 的全局替换功能，或者使用 sed 命令（Linux/Mac）
find src/main/java -name "*.java" -type f -exec sed -i 's/jakarta\.servlet/javax.servlet/g' {} \;

# Windows PowerShell
Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object {
    (Get-Content $_.FullName) -replace 'jakarta\.servlet', 'javax.servlet' | Set-Content $_.FullName
}
```

### 3. 测试代码检查

检查测试代码中是否使用了：
- Jakarta Servlet API（需要替换为 javax.servlet）
- 其他 Java 11+ 的特性

**测试文件位置：**
- `src/test/java/io/github/http/log/snap/demo/`

### 4. 文档更新

更新以下文档中的 Java 版本说明：

1. **`README.md`**
   - 更新 Java 版本徽章：`![Java](https://img.shields.io/badge/Java-8-orange?logo=openjdk)`
   - 更新版本要求说明

2. **`docs/guide.md`**
   - 更新依赖说明表格中的版本要求

3. **`docs/advanced.md`**
   - 如有版本相关说明，需要更新

### 5. CI/CD 配置检查

检查 `.github/workflows/` 中的 CI 配置，确保：
- 使用 JDK 1.8 进行编译和测试
- 测试环境配置正确

## ⚠️ 重要注意事项

1. **Spring Boot 2.7.x 是最后一个支持 JDK 1.8 的版本**，后续版本不再支持。
2. **OkHttp 5.3.0 支持 Java 8+**，无需降级版本。
3. **SLF4J 1.7.36 是最后一个支持 JDK 1.8 的版本**，SLF4J 2.x 需要 Java 11+。
4. **Jakarta EE 9+ 需要 Java 11+**，JDK 1.8 只能使用 javax.servlet。
5. **语法特性已兼容**：主分支中的 instanceof 模式匹配、switch 表达式、var 关键字已经全部改为兼容写法，无需在 JDK 1.8 分支中再次修改。
6. 修改后需要充分测试，确保功能正常。

## 🔄 迁移步骤建议

1. 创建新分支：`git checkout -b support-jdk8`
2. 修改 `pom.xml`：
   - Java 版本改为 1.8
   - 所有依赖版本降级
   - Servlet API 从 Jakarta 改为 javax
3. 批量替换代码中的 `jakarta.servlet` → `javax.servlet`（6个文件）
4. 检查并修改测试代码
5. 更新文档中的版本说明
6. 运行测试确保功能正常
7. 验证编译和打包

## 📝 验证清单

- [ ] pom.xml Java 版本改为 1.8
- [ ] 所有依赖版本降级完成（Lombok, SLF4J, OkHttp, Spring, Spring Boot, Servlet API, JUnit, Mockito, Logback）
- [ ] Servlet API 依赖从 Jakarta 改为 javax
- [ ] 代码中所有 `jakarta.servlet` 替换为 `javax.servlet`（6个文件）
- [ ] 测试代码检查完成
- [ ] 文档更新完成（README.md, docs/guide.md, docs/advanced.md）
- [ ] 编译通过
- [ ] 单元测试通过
- [ ] 功能测试通过

## 📊 修改统计

- **pom.xml 修改**：约 10 处依赖版本修改
- **代码文件修改**：6 个文件中的 import 语句
- **文档更新**：3 个文件

## 🔍 快速检查命令

```bash
# 检查是否还有 jakarta.servlet 引用
grep -r "jakarta\.servlet" src/main/java

# 检查 pom.xml 中的版本
grep -E "(version|servlet)" pom.xml | grep -E "(jakarta|21|3\.|5\.|2\.0)"

# 验证编译
mvn clean compile -DskipTests
```
