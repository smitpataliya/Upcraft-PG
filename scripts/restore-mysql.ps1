<#
.SYNOPSIS
  Restores a MySQL backup (.sql or .zip) safely into an isolated verification schema.
  Refuses to target live 'pg_management' unless -ForceLiveOverwrite YES is passed explicitly.
#>
param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$TargetSchema = "pg_management_restore_verify",
    [string]$ForceLiveOverwrite = "NO",
    [string]$ContainerName = "upcraft-pg-mysql",
    [string]$Username = $(if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }),
    [string]$Password = $(if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } elseif ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "" }),
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "MYSQL_ROOT_PASSWORD or MYSQL_PASSWORD environment variable must be set."
}
if ($TargetSchema -eq "pg_management" -and $ForceLiveOverwrite -ne "YES") {
    throw "SAFETY BLOCK: Refusing to overwrite live 'pg_management' without -ForceLiveOverwrite YES. Use an isolated schema like 'pg_management_restore_verify'."
}

$sqlToRestore = $BackupFile
$tempDir = $null
if ($BackupFile.EndsWith(".zip")) {
    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("upcraft-restore-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    Expand-Archive -Path $BackupFile -DestinationPath $tempDir -Force
    $extracted = Get-ChildItem -Path $tempDir -Filter "*.sql" | Select-Object -First 1
    if (-not $extracted) {
        throw "No .sql file found inside $BackupFile"
    }
    $sqlToRestore = $extracted.FullName
}

$runningContainers = @()
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $runningContainers = docker ps --format "{{.Names}}"
}

try {
    $createSql = "CREATE DATABASE IF NOT EXISTS ``$TargetSchema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($runningContainers -contains $ContainerName) {
        docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql -u $Username -e $createSql
        Get-Content -Raw -Path $sqlToRestore | docker exec -i -e "MYSQL_PWD=$Password" $ContainerName mysql -u $Username $TargetSchema
        $tables = docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql -u $Username -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$TargetSchema';"
    }
    else {
        $env:MYSQL_PWD = $Password
        try {
            mysql -h $DbHost -P $DbPort -u $Username -e $createSql
            Get-Content -Raw -Path $sqlToRestore | mysql -h $DbHost -P $DbPort -u $Username $TargetSchema
            $tables = mysql -h $DbHost -P $DbPort -u $Username -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$TargetSchema';"
        }
        finally {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
    Write-Host "Restore verified safely into '$TargetSchema' (table count: $tables)."
}
finally {
    if ($tempDir -and (Test-Path $tempDir)) {
        Remove-Item -Recurse -Force $tempDir
    }
}

