<#
.SYNOPSIS
  游戏陪玩系统 —— 一键部署到阿里云 ECS。

.DESCRIPTION
  流程：构建后端 jar + 前端 dist -> 打包 -> scp 上传到 /tmp/gameplay-deploy -> 执行远端部署脚本。
  远端脚本负责备份、建库、切换版本、安装 systemd 服务、热重载 nginx 与健康检查。
  密钥从 deploy/gameplay.env 读取，该文件不入库。

  传输走 SSH（需先用 deploy/ecs-runcommand.ps1 把本机公钥装到服务器）。
  不用 workbench upload 的原因：Workbench 对会话数有服务端配额，批量传输会触发
  Forbidden.SessionLimit，且 35MB 的 jar 走 OSS 中转明显更慢。

.EXAMPLE
  pwsh -File deploy\deploy.ps1                 # 全量构建并部署
  pwsh -File deploy\deploy.ps1 -SkipBuild      # 用现有产物直接部署
#>
param(
  [string]$Server = '120.27.144.223',
  [string]$SshKey = (Join-Path $env:USERPROFILE '.ssh\gamemate_ecs_ed25519'),
  [string]$Maven = 'D:\AI_xiangmu\陪玩系统\.worktrees\tools\apache-maven-3.9.15\bin\mvn.cmd',
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$staging = Join-Path $PSScriptRoot '.staging'
$envFile = Join-Path $PSScriptRoot 'gameplay.env'
$jar = Join-Path $repo 'backend\target\gameplay-backend-0.1.0-SNAPSHOT.jar'
$dist = Join-Path $repo 'frontend\dist'
$remote = '/tmp/gameplay-deploy'

$sshOpts = @('-i', $SshKey, '-o', 'StrictHostKeyChecking=no', '-o', 'BatchMode=yes')
$target = "root@$Server"

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

if (-not (Test-Path $envFile)) { throw "缺少 $envFile（可由 gameplay.env.example 复制并填写）" }
if (-not (Test-Path $SshKey)) { throw "找不到 SSH 私钥: $SshKey" }

if (-not $SkipBuild) {
  Step '构建后端 jar'
  & $Maven -f (Join-Path $repo 'backend\pom.xml') clean package -DskipTests -B
  if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
  Step '构建前端 dist'
  Push-Location (Join-Path $repo 'frontend')
  try { npm run build; if ($LASTEXITCODE -ne 0) { throw '前端构建失败' } } finally { Pop-Location }
}

if (-not (Test-Path $jar)) { throw "找不到后端产物: $jar" }
if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw "找不到前端产物: $dist" }

Step "准备暂存目录 $staging"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

Copy-Item $jar (Join-Path $staging 'gameplay-backend.jar') -Force
Copy-Item (Join-Path $repo 'sql\schema.sql') $staging -Force
Copy-Item (Join-Path $repo 'sql\data.sql') $staging -Force
foreach ($f in 'gameplay.service', 'application-prod.yml', 'nginx-gamemate.conf') {
  Copy-Item (Join-Path $PSScriptRoot $f) $staging -Force
}
# shell 脚本强制 LF：Windows 检出后若为 CRLF，Linux 上 bash 会报 $'\\r': command not found
$shText = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'remote-deploy.sh')) -replace ([string][char]13 + [string][char]10), [string][char]10
[System.IO.File]::WriteAllText((Join-Path $staging 'remote-deploy.sh'), $shText, (New-Object System.Text.UTF8Encoding($false)))
Copy-Item $envFile (Join-Path $staging 'gameplay.env') -Force

Step '打包前端 dist'
tar -czf (Join-Path $staging 'frontend.tar.gz') -C $dist .
if ($LASTEXITCODE -ne 0) { throw 'tar 打包失败' }

Step "上传到 $target 的 $remote"
& ssh @sshOpts $target "rm -rf $remote && mkdir -p $remote"
$files = Get-ChildItem $staging -File
foreach ($f in $files) { Write-Host "    $($f.Name) ($([math]::Round($f.Length/1KB)) KB)" }
# 注意：必须写成 ${target}，否则 PowerShell 会把 "$target:" 当成作用域变量引用而报错
$scpArgs = @($sshOpts + @('-q') + ($files | ForEach-Object { $_.FullName }) + @("${target}:$remote/"))
& scp @scpArgs
if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败' }

Step '执行远端部署'
& ssh @sshOpts $target "bash $remote/remote-deploy.sh"
if ($LASTEXITCODE -ne 0) { throw '远端部署脚本执行失败' }

Write-Host ''
Write-Host '部署完成。' -ForegroundColor Green
