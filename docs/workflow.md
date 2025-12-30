# 开发与发布流程

本文档定义了 HTTP Log Snap 项目的开发约束、规范和发布流程，确保 JDK 21 和 JDK 1.8 两个版本的代码保持一致。

## 🎯 核心原则

### 1. 主开发分支：JDK 21 项目（main 分支）

**所有新功能开发和代码迭代都必须在 JDK 21 项目中进行。**

- ✅ 在 `main` 分支（JDK 21 项目）中开发新功能
- ✅ 在 `main` 分支中修复 bug
- ✅ 在 `main` 分支中重构代码
- ✅ 在 `main` 分支中更新文档

### 2. JDK 1.8 项目：仅同步代码

**JDK 1.8 项目（support-jdk8 分支）只通过拉取主分支代码进行迭代。**

- ✅ 通过 `git merge main` 从主分支同步代码
- ✅ 应用 JDK 1.8 兼容性修复
- ❌ **禁止**在 support-jdk8 分支中直接开发新功能
- ❌ **禁止**在 support-jdk8 分支中直接修复 bug（除非是 JDK 1.8 特有的问题）

## 📋 日常开发流程

### 1. 在 JDK 21 项目中开发

```bash
# 切换到主分支
git checkout main
git pull origin main

# 创建功能分支
git checkout -b feature/new-feature

# 开发、测试、提交
git add .
git commit -m "feat: add new feature"
git push origin feature/new-feature
```

### 2. 代码审查和合并

- 创建 Pull Request 到 `main` 分支
- 代码审查通过后合并到 `main`

### 3. 同步到 JDK 1.8 分支

```bash
# 切换到 support-jdk8 分支
git checkout support-jdk8
git pull origin support-jdk8

# 合并主分支的代码
git merge main

# 解决可能的冲突
# 应用 JDK 1.8 兼容性修复（如果需要）

# 提交并推送
git push origin support-jdk8
```

### 4. JDK 1.8 兼容性修复

当从主分支同步代码到 support-jdk8 分支后，可能需要应用兼容性修复：

1. **检查编译错误**
   ```bash
   cd http-log-snap-jdk1.8
   mvn clean compile
   ```

2. **应用兼容性修复**
   - 使用 JDK 1.8 兼容的 API 替换不兼容的方法
   - 参考已有的兼容性修复模式
   - 确保功能逻辑与 JDK 21 版本完全一致

3. **验证修复**
   ```bash
   mvn clean test
   ```

## 🔒 开发约束规则

### ✅ 允许的操作

- 在 `main` 分支中开发所有新功能
- 在 `main` 分支中修复所有 bug
- 在 `main` 分支中重构代码
- 在 `main` 分支中更新文档
- 在 `support-jdk8` 分支中合并 `main` 分支的代码
- 在 `support-jdk8` 分支中应用 JDK 1.8 兼容性修复
- 在 `support-jdk8` 分支中修复 JDK 1.8 特有的问题

### ❌ 禁止的操作

- ❌ 在 `support-jdk8` 分支中直接开发新功能
- ❌ 在 `support-jdk8` 分支中直接修复 bug（除非是 JDK 1.8 特有的问题）
- ❌ 在 `support-jdk8` 分支中修改业务逻辑
- ❌ 在 `support-jdk8` 分支中修改功能实现（只允许兼容性修复）
- ❌ 在 `support-jdk8` 分支中创建独立的功能分支

## 📝 代码同步检查清单

在将代码从 `main` 同步到 `support-jdk8` 后，请检查：

- [ ] 代码已成功合并，无冲突
- [ ] 所有 JDK 1.8 不兼容的 API 已修复
- [ ] 编译通过（`mvn clean compile`）
- [ ] 测试通过（`mvn clean test`）
- [ ] 功能逻辑与 JDK 21 版本一致
- [ ] 代码已提交并推送到 `support-jdk8` 分支

## 🚀 发布流程

### 📋 发布前准备

#### 1. 确保代码已合并
- 所有功能开发在 **JDK 21 项目**（主分支）中完成
- 确保所有更改已提交并推送到主分支
- 运行测试确保所有功能正常

#### 2. 更新版本号
- 在 `pom.xml` 中更新版本号（如 `0.0.11`）
- 更新 `CHANGELOG.md`，添加新版本的更新内容

#### 3. 提交更改
```bash
git add .
git commit -m "chore: bump version to 0.0.11"
git push origin main
```

### 发布步骤

#### 步骤 1: 创建 JDK 21 版本的 Tag

在主分支（JDK 21 项目）创建 tag：

```bash
# 切换到主分支
git checkout main
git pull origin main

# 创建 tag（不带 v 前缀）
git tag 0.0.11
git push origin 0.0.11
```

#### 步骤 2: 同步代码到 JDK 1.8 分支

将主分支的代码同步到 `support-jdk8` 分支：

```bash
# 切换到 support-jdk8 分支
git checkout support-jdk8
git pull origin support-jdk8

# 合并主分支的代码
git merge main

# 解决可能的冲突（如果有）
# 确保所有 JDK 1.8 兼容性修复已应用

# 提交合并
git push origin support-jdk8
```

#### 步骤 3: 创建 JDK 1.8 版本的 Tag

在 `support-jdk8` 分支创建 JDK 1.8 版本的 tag：

```bash
# 确保在 support-jdk8 分支
git checkout support-jdk8

# 创建 JDK 8 版本的 tag（格式：版本号-jdk8）
git tag 0.0.11-jdk8
git push origin 0.0.11-jdk8
```

#### 步骤 4: 创建 GitHub Release

在 GitHub 上创建 Release：

1. 访问仓库的 Releases 页面
2. 点击 "Draft a new release"
3. 选择 tag：`0.0.11`（JDK 21 版本的 tag）
4. 填写 Release 标题：`v0.0.11`（可选，带 v 前缀）
5. 填写 Release 说明（可以从 CHANGELOG.md 复制）
6. 点击 "Publish release"

#### 步骤 5: 自动发布到 Maven Central

创建 Release 后，GitHub Actions 会自动触发发布流程：

- **JDK 21 版本**：使用 tag `0.0.11` 构建并发布 `http-log-snap`
- **JDK 8 版本**：使用 tag `0.0.11-jdk8` 构建并发布 `http-log-snap-jdk8`
- **代码一致性检查**：自动验证 JDK 8 版本的 tag 是否基于 JDK 21 版本的 tag

#### 步骤 6: 验证发布

1. 等待 GitHub Actions 构建完成
2. 检查 Maven Central 是否已发布新版本
3. 验证两个版本都可以正常下载

### ⚠️ 发布注意事项

1. **Tag 格式**：
   - JDK 21 版本：`0.0.11`（不带 v 前缀）
   - JDK 8 版本：`0.0.11-jdk8`（不带 v 前缀）

2. **代码一致性**：
   - JDK 8 版本的 tag 必须基于 JDK 21 版本的 tag
   - Workflow 会自动验证，如果不一致会构建失败

3. **发布顺序**：
   - 必须先创建 JDK 21 的 tag
   - 然后同步代码到 support-jdk8 分支
   - 最后创建 JDK 8 的 tag
   - 最后创建 GitHub Release

4. **如果构建失败**：
   - 检查 tag 是否正确创建
   - 检查代码是否已同步
   - 检查代码一致性验证是否通过

### 📝 发布检查清单

- [ ] 版本号已更新（pom.xml）
- [ ] CHANGELOG.md 已更新
- [ ] 所有更改已提交并推送到主分支
- [ ] 测试通过
- [ ] 创建 JDK 21 版本的 tag（`0.0.11`）
- [ ] 同步代码到 support-jdk8 分支
- [ ] 创建 JDK 8 版本的 tag（`0.0.11-jdk8`）
- [ ] 创建 GitHub Release（使用 `0.0.11` tag）
- [ ] 验证 GitHub Actions 构建成功
- [ ] 验证 Maven Central 发布成功

## 🎯 目标

遵循这些流程和约束可以确保：

1. **代码一致性**：两个版本的代码逻辑完全一致
2. **维护效率**：只需在一个地方（JDK 21 项目）进行开发
3. **质量保证**：JDK 1.8 版本始终基于最新的 JDK 21 代码
4. **发布可靠性**：发布流程可以自动验证代码一致性

## 📚 相关文档

- [使用指南](./guide.md) - 用户使用文档
- [高级用法](./advanced.md) - 高级功能文档

