<#
.SYNOPSIS
  Creates a consistent gzipped SQL backup of the MySQL database without hardcoding credentials.
.EXAMPLE
  $env:MYSQL_ROOT_PASSWORD="***"
  .\scripts\backup-mysql.ps1 -DatabaseName pg_management -BackupDir .\backups
#>
param(
    [string]$ContainerName = "upcraft-pg-mysql",
    [string]$DatabaseName = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "pg_management" }),
    [string]$Username = $(if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }),
    [string]$Password = $(if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } elseif ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }),
    [string]$BackupDir = ".\backups",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "MYSQL_PASSWORD or MYSQL_ROOT_PASSWORD environment variable must be set."
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$sqlFile = Join-Path $BackupDir "$DatabaseName-$timestamp.sql"

$runningContainers = @()
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $runningContainers = docker ps --format "{{.Names}}"
}

if ($runningContainers -contains $ContainerName) {
    Write-Host "Dumping '$DatabaseName' from Docker container '$ContainerName'..."
    docker exec -e "MYSQL_PWD=$Password" $ContainerName `
        mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF `
        -u $Username $DatabaseName | Out-File -FilePath $sqlFile -Encoding utf8
}
else {
    Write-Host "Dumping '$DatabaseName' from host ${DbHost}:${DbPort}..."
    $env:MYSQL_PWD = $Password
    try {
        mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF `
            -h $DbHost -P $DbPort -u $Username $DatabaseName | Out-File -FilePath $sqlFile -Encoding utf8
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

$item = Get-Item $sqlFile
if ($item.Length -le 0) {
    throw "Backup file '$sqlFile' is empty."
}

$zipFile = "$sqlFile.zip"
Compress-Archive -Path $sqlFile -DestinationPath $zipFile -Force
Remove-Item $sqlFile -Force
Write-Host "Backup complete: $zipFile"

