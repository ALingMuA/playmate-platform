<#
.SYNOPSIS
  直接调用阿里云 ECS OpenAPI（V1 签名），用于在不消耗 Workbench 会话的情况下
  通过云助手 RunCommand 在实例上执行命令。凭据从 ~/.workbench/config.json 读取。

.EXAMPLE
  pwsh -File deploy\ecs-runcommand.ps1 -Action DescribeCloudAssistantStatus
  pwsh -File deploy\ecs-runcommand.ps1 -Action RunCommand -Script "echo hi"
#>
param(
  [Parameter(Mandatory = $true)][string]$Action,
  [string]$InstanceId = 'i-bp1g2jk5s1tq8lsa1r3p',
  [string]$RegionId = 'cn-hangzhou',
  [string]$Script,
  [string]$InvokeId,
  [int]$Timeout = 60
)

$ErrorActionPreference = 'Stop'

function Get-PercentEncoded([string]$s) {
  $sb = New-Object System.Text.StringBuilder
  foreach ($b in [System.Text.Encoding]::UTF8.GetBytes($s)) {
    $isUnreserved = (($b -ge 65 -and $b -le 90) -or ($b -ge 97 -and $b -le 122) -or
                     ($b -ge 48 -and $b -le 57) -or $b -eq 45 -or $b -eq 95 -or $b -eq 46 -or $b -eq 126)
    if ($isUnreserved) { [void]$sb.Append([char]$b) } else { [void]$sb.Append('%' + $b.ToString('X2')) }
  }
  $sb.ToString()
}

$cfg = Get-Content (Join-Path $env:USERPROFILE '.workbench\config.json') -Raw | ConvertFrom-Json
$ak = $cfg.profiles.default.access_key_id
$sk = $cfg.profiles.default.access_key_secret

$common = [ordered]@{
  Format           = 'JSON'
  Version          = '2014-05-26'
  AccessKeyId      = $ak
  SignatureMethod  = 'HMAC-SHA1'
  Timestamp        = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  SignatureVersion = '1.0'
  SignatureNonce   = [guid]::NewGuid().ToString()
  Action           = $Action
  RegionId         = $RegionId
}
$params = [ordered]@{}
foreach ($k in $common.Keys) { $params[$k] = $common[$k] }

switch ($Action) {
  'DescribeCloudAssistantStatus' {
    $params['InstanceId.1'] = $InstanceId
  }
  'RunCommand' {
    if (-not $Script) { throw 'RunCommand 需要 -Script' }
    $params['InstanceId.1']   = $InstanceId
    $params['Type']           = 'RunShellScript'
    $params['ContentEncoding'] = 'Base64'
    $params['CommandContent'] = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Script))
    $params['Timeout']        = $Timeout
  }
  'DescribeInvocationResults' {
    if (-not $InvokeId) { throw 'DescribeInvocationResults 需要 -InvokeId' }
    $params['InvokeId']     = $InvokeId
    $params['InstanceId.1'] = $InstanceId
  }
  default { throw "未支持的 Action: $Action" }
}

# 按 ordinal 排序（不能用 Sort-Object，它是区域性排序）
$keys = [string[]]@($params.Keys)
[Array]::Sort($keys, [System.StringComparer]::Ordinal)
$cqs = ($keys | ForEach-Object { (Get-PercentEncoded $_) + '=' + (Get-PercentEncoded ([string]$params[$_])) }) -join '&'

$stringToSign = 'POST&' + (Get-PercentEncoded '/') + '&' + (Get-PercentEncoded $cqs)
$hmac = New-Object System.Security.Cryptography.HMACSHA1
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($sk + '&')
$sig = [Convert]::ToBase64String($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($stringToSign)))

$body = $cqs + '&Signature=' + (Get-PercentEncoded $sig)
Invoke-RestMethod -Uri 'https://ecs.aliyuncs.com/' -Method Post -Body $body -ContentType 'application/x-www-form-urlencoded'
