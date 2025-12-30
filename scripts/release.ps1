# HTTP Log Snap 发布脚本 (PowerShell 版本)
# 自动检查版本、CHANGELOG、未提交的更改，并创建相应的 tag

$ErrorActionPreference = "Stop"

# 颜色函数
function Write-Info {
    param([string]$Message)
    Write-Host "ℹ $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

# 获取脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# 切换到项目根目录
Set-Location $ProjectRoot

Write-Info "项目根目录: $ProjectRoot"

# 1. 获取当前分支
$CurrentBranch = git rev-parse --abbrev-ref HEAD
Write-Info "当前分支: $CurrentBranch"

# 判断是 JDK 21 还是 JDK 8 项目
$ProjectType = ""
$TagSuffix = ""
$TargetBranch = ""
$OtherBranch = ""

if ($CurrentBranch -eq "main" -or $CurrentBranch -eq "master") {
    $ProjectType = "jdk21"
    $TagSuffix = ""
    $TargetBranch = "main"
    $OtherBranch = "support-jdk8"
} elseif ($CurrentBranch -eq "support-jdk8") {
    $ProjectType = "jdk8"
    $TagSuffix = "-jdk8"
    $TargetBranch = "support-jdk8"
    $OtherBranch = "main"
} else {
    Write-Error "当前分支 '$CurrentBranch' 不是 main/master 或 support-jdk8"
    Write-Info "请在 main/master 分支执行 JDK 21 的发布，或在 support-jdk8 分支执行 JDK 8 的发布"
    exit 1
}

Write-Info "项目类型: $ProjectType"
Write-Info "Tag 后缀: $(if ($TagSuffix) { $TagSuffix } else { '无' })"

# 2. 读取 pom.xml 获取版本号
if (-not (Test-Path "pom.xml")) {
    Write-Error "找不到 pom.xml 文件"
    exit 1
}

# 从 pom.xml 中提取版本号
$pomContent = Get-Content "pom.xml" -Raw
$versionMatch = [regex]::Match($pomContent, '<version>([^<]+)</version>')
if ($versionMatch.Success) {
    $Version = $versionMatch.Groups[1].Value.Trim()
} else {
    Write-Error "无法从 pom.xml 中读取版本号"
    exit 1
}

Write-Success "当前版本: $Version"

# 3. 检查 CHANGELOG.md 是否有当前版本的说明
if (-not (Test-Path "CHANGELOG.md")) {
    Write-Warning "找不到 CHANGELOG.md 文件"
    $response = Read-Host "是否继续？(y/n)"
    if ($response -ne "y" -and $response -ne "Y") {
        exit 1
    }
} else {
    $changelogContent = Get-Content "CHANGELOG.md" -Raw
    if ($changelogContent -match "\[$Version\]" -or $changelogContent -match "## \[$Version\]" -or $changelogContent -match "## $Version") {
        Write-Success "CHANGELOG.md 中包含版本 $Version 的说明"
    } else {
        Write-Warning "CHANGELOG.md 中未找到版本 $Version 的说明"
        $response = Read-Host "是否继续？(y/n)"
        if ($response -ne "y" -and $response -ne "Y") {
            exit 1
        }
    }
}

# 4. 检查是否有未提交的更改
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Error "存在未提交的更改，请先提交所有更改后再运行此脚本"
    exit 1
}

# 检查是否有未推送的提交
try {
    # 获取远程分支名称
    $remoteBranch = "origin/$CurrentBranch"
    
    # 检查远程分支是否存在
    $remoteExists = git ls-remote --heads origin $CurrentBranch 2>$null
    if ($remoteExists) {
        # 比较本地和远程的提交
        $localCommit = git rev-parse HEAD 2>$null
        $remoteCommit = git rev-parse $remoteBranch 2>$null
        
        if ($localCommit -and $remoteCommit -and $localCommit -ne $remoteCommit) {
            # 检查本地是否有未推送的提交
            $aheadCommits = git rev-list "$remoteBranch..HEAD" 2>$null
            if ($aheadCommits) {
                $commitCount = ($aheadCommits | Measure-Object -Line).Lines
                if ($commitCount -gt 0) {
                    Write-Error "存在 $commitCount 个未推送的提交，请先推送所有提交后再运行此脚本"
                    exit 1
                }
            }
        }
    }
} catch {
    # 如果没有设置远程分支，忽略错误
    $null
}

Write-Success "所有更改已提交并推送"

# 5. 检查 tag 是否已存在
$TagName = "$Version$TagSuffix"
$tagExists = git rev-parse "$TagName" 2>$null
if ($tagExists) {
    Write-Warning "Tag '$TagName' 已存在"
    $tagCommit = git rev-parse "$TagName"
    $currentCommit = git rev-parse HEAD
    Write-Info "Tag 指向的提交: $tagCommit"
    Write-Info "当前提交: $currentCommit"
    
    if ($tagCommit -eq $currentCommit) {
        Write-Info "Tag 已指向当前提交"
    } else {
        Write-Warning "Tag 指向的提交与当前提交不同"
    }
    
    $response = Read-Host "是否删除现有 tag 并重新创建？(y/n)"
    if ($response -eq "y" -or $response -eq "Y") {
        Write-Info "正在删除本地 tag..."
        git tag -d "$TagName" 2>$null
        
        # 检查远程是否存在
        $remoteTags = git ls-remote --tags origin 2>$null
        if ($remoteTags -match "refs/tags/$TagName") {
            Write-Info "正在删除远程 tag..."
            git push origin ":refs/tags/$TagName" 2>$null
        }
        Write-Success "Tag 已删除"
    } else {
        Write-Info "跳过 tag 创建"
        exit 0
    }
}

# 6. 确保在正确的分支上
if ((git rev-parse --abbrev-ref HEAD) -ne $TargetBranch) {
    Write-Warning "当前不在 $TargetBranch 分支"
    $response = Read-Host "是否切换到 $TargetBranch 分支？(y/n)"
    if ($response -eq "y" -or $response -eq "Y") {
        Write-Info "正在切换到 $TargetBranch 分支..."
        git checkout $TargetBranch
        git pull origin $TargetBranch
    } else {
        Write-Error "请在 $TargetBranch 分支上执行此脚本"
        exit 1
    }
}

# 7. 创建 tag
Write-Info "正在创建 tag: $TagName"
$tagMessage = "Release version $Version"
if ($TagSuffix) {
    $tagMessage += " (JDK 8)"
}
git tag -a "$TagName" -m $tagMessage

Write-Success "Tag '$TagName' 已创建"

# 8. 推送 tag
$response = Read-Host "是否推送 tag 到远程仓库？(y/n)"
if ($response -eq "y" -or $response -eq "Y") {
    Write-Info "正在推送 tag..."
    git push origin "$TagName"
    Write-Success "Tag '$TagName' 已推送到远程仓库"
} else {
    Write-Warning "Tag 未推送，请稍后手动推送: git push origin $TagName"
}

# 9. 如果是 JDK 21，提示同步到 JDK 8 分支
if ($ProjectType -eq "jdk21") {
    Write-Host ""
    Write-Info "下一步操作："
    Write-Info "1. 切换到 support-jdk8 分支: git checkout support-jdk8"
    Write-Info "2. 合并 main 分支的代码: git merge main"
    Write-Info "3. 应用 JDK 1.8 兼容性修复（如果需要）"
    Write-Info "4. 在 support-jdk8 分支运行此脚本创建 JDK 8 版本的 tag"
    Write-Host ""
    Write-Info "或者运行以下命令自动处理:"
    Write-Info "  git checkout support-jdk8"
    Write-Info "  git merge main"
    Write-Info "  git push origin support-jdk8"
    Write-Info "  .\scripts\release.ps1"
}

# 10. 如果是 JDK 8，提示创建 GitHub Release
if ($ProjectType -eq "jdk8") {
    Write-Host ""
    Write-Info "下一步操作："
    Write-Info "1. 在 GitHub 上创建 Release"
    Write-Info "2. 选择 tag: $Version (JDK 21 版本的 tag)"
    Write-Info "3. GitHub Actions 会自动构建并发布两个版本到 Maven Central"
}

Write-Host ""
Write-Success "发布流程完成！"

