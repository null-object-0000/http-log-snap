#!/bin/bash

# HTTP Log Snap 发布脚本
# 自动检查版本、CHANGELOG、未提交的更改，并创建相应的 tag

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 切换到项目根目录
cd "$PROJECT_ROOT"

print_info "项目根目录: $PROJECT_ROOT"

# 1. 获取当前分支
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
print_info "当前分支: $CURRENT_BRANCH"

# 判断是 JDK 21 还是 JDK 8 项目
if [[ "$CURRENT_BRANCH" == "main" ]] || [[ "$CURRENT_BRANCH" == "master" ]]; then
    PROJECT_TYPE="jdk21"
    TAG_SUFFIX=""
    TARGET_BRANCH="main"
    OTHER_BRANCH="support-jdk8"
elif [[ "$CURRENT_BRANCH" == "support-jdk8" ]]; then
    PROJECT_TYPE="jdk8"
    TAG_SUFFIX="-jdk8"
    TARGET_BRANCH="support-jdk8"
    OTHER_BRANCH="main"
else
    print_error "当前分支 '$CURRENT_BRANCH' 不是 main/master 或 support-jdk8"
    print_info "请在 main/master 分支执行 JDK 21 的发布，或在 support-jdk8 分支执行 JDK 8 的发布"
    exit 1
fi

print_info "项目类型: $PROJECT_TYPE"
print_info "Tag 后缀: ${TAG_SUFFIX:-无}"

# 2. 读取 pom.xml 获取版本号
if [ ! -f "pom.xml" ]; then
    print_error "找不到 pom.xml 文件"
    exit 1
fi

# 从 pom.xml 中提取版本号（查找 <version> 标签，排除 parent 和 dependency 中的版本）
VERSION=$(grep -E "^[[:space:]]*<version>" pom.xml | head -1 | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/' | xargs)

# 如果还是找不到，尝试查找 artifactId 后面的 version
if [ -z "$VERSION" ] || [ "$VERSION" = "" ]; then
    # 查找 <artifactId>http-log-snap</artifactId> 或 <artifactId>http-log-snap-jdk8</artifactId> 后面的 <version>
    VERSION=$(awk '/<artifactId>http-log-snap/,/<\/version>/ {if (/<version>/) {gsub(/.*<version>|<\/version>.*/, ""); print; exit}}' pom.xml | xargs)
fi

# 如果还是找不到，尝试更通用的方法
if [ -z "$VERSION" ] || [ "$VERSION" = "" ]; then
    # 查找第一个独立的 <version> 标签（不在注释中）
    VERSION=$(grep -v "^[[:space:]]*<!--" pom.xml | grep -E "^[[:space:]]*<version>" | head -1 | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/' | xargs)
fi

if [ -z "$VERSION" ] || [ "$VERSION" = "" ]; then
    print_error "无法从 pom.xml 中读取版本号"
    print_info "请确保 pom.xml 中包含 <version> 标签"
    exit 1
fi

print_success "当前版本: $VERSION"

# 3. 检查 CHANGELOG.md 是否有当前版本的说明
if [ ! -f "CHANGELOG.md" ]; then
    print_warning "找不到 CHANGELOG.md 文件"
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    # 检查 CHANGELOG 中是否包含当前版本
    if grep -q "\[$VERSION\]" CHANGELOG.md || grep -q "## \[$VERSION\]" CHANGELOG.md || grep -q "## $VERSION" CHANGELOG.md; then
        print_success "CHANGELOG.md 中包含版本 $VERSION 的说明"
    else
        print_warning "CHANGELOG.md 中未找到版本 $VERSION 的说明"
        read -p "是否继续？(y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# 4. 检查是否有未提交的更改
if ! git diff-index --quiet HEAD --; then
    print_error "存在未提交的更改，请先提交所有更改后再运行此脚本"
    exit 1
fi

# 检查是否有未推送的提交
LOCAL_COMMITS=$(git rev-list @{u}..HEAD 2>/dev/null | wc -l || echo "0")
if [ "$LOCAL_COMMITS" -gt 0 ]; then
    print_error "存在 $LOCAL_COMMITS 个未推送的提交，请先推送所有提交后再运行此脚本"
    exit 1
fi

print_success "所有更改已提交并推送"

# 5. 检查 tag 是否已存在
TAG_NAME="${VERSION}${TAG_SUFFIX}"
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    print_warning "Tag '$TAG_NAME' 已存在"
    TAG_COMMIT=$(git rev-parse "$TAG_NAME")
    CURRENT_COMMIT=$(git rev-parse HEAD)
    print_info "Tag 指向的提交: $TAG_COMMIT"
    print_info "当前提交: $CURRENT_COMMIT"
    
    if [ "$TAG_COMMIT" = "$CURRENT_COMMIT" ]; then
        print_info "Tag 已指向当前提交"
    else
        print_warning "Tag 指向的提交与当前提交不同"
    fi
    
    read -p "是否删除现有 tag 并重新创建？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "正在删除本地 tag..."
        git tag -d "$TAG_NAME" || true
        
        # 检查远程是否存在
        if git ls-remote --tags origin | grep -q "refs/tags/$TAG_NAME"; then
            print_info "正在删除远程 tag..."
            git push origin ":refs/tags/$TAG_NAME" || true
        fi
        print_success "Tag 已删除"
    else
        print_info "跳过 tag 创建"
        exit 0
    fi
fi

# 6. 确保在正确的分支上
if [ "$(git rev-parse --abbrev-ref HEAD)" != "$TARGET_BRANCH" ]; then
    print_warning "当前不在 $TARGET_BRANCH 分支"
    read -p "是否切换到 $TARGET_BRANCH 分支？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "正在切换到 $TARGET_BRANCH 分支..."
        git checkout "$TARGET_BRANCH"
        git pull origin "$TARGET_BRANCH"
    else
        print_error "请在 $TARGET_BRANCH 分支上执行此脚本"
        exit 1
    fi
fi

# 7. 创建 tag
print_info "正在创建 tag: $TAG_NAME"
git tag -a "$TAG_NAME" -m "Release version $VERSION${TAG_SUFFIX:+ (JDK 8)}"

print_success "Tag '$TAG_NAME' 已创建"

# 8. 推送 tag
read -p "是否推送 tag 到远程仓库？(y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "正在推送 tag..."
    git push origin "$TAG_NAME"
    print_success "Tag '$TAG_NAME' 已推送到远程仓库"
else
    print_warning "Tag 未推送，请稍后手动推送: git push origin $TAG_NAME"
fi

# 9. 如果是 JDK 21，提示同步到 JDK 8 分支
if [ "$PROJECT_TYPE" = "jdk21" ]; then
    echo
    print_info "下一步操作："
    print_info "1. 切换到 support-jdk8 分支: git checkout support-jdk8"
    print_info "2. 合并 main 分支的代码: git merge main"
    print_info "3. 应用 JDK 1.8 兼容性修复（如果需要）"
    print_info "4. 在 support-jdk8 分支运行此脚本创建 JDK 8 版本的 tag"
    echo
    print_info "或者运行以下命令自动处理:"
    print_info "  git checkout support-jdk8"
    print_info "  git merge main"
    print_info "  git push origin support-jdk8"
    print_info "  ./scripts/release.sh"
fi

# 10. 如果是 JDK 8，提示创建 GitHub Release
if [ "$PROJECT_TYPE" = "jdk8" ]; then
    echo
    print_info "下一步操作："
    print_info "1. 在 GitHub 上创建 Release"
    print_info "2. 选择 tag: $VERSION (JDK 21 版本的 tag)"
    print_info "3. GitHub Actions 会自动构建并发布两个版本到 Maven Central"
fi

echo
print_success "发布流程完成！"

