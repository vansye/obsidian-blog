[CmdletBinding()]
param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$repoDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $repoDir
Get-Command git -ErrorAction Stop | Out-Null
function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
if (-not $PrepareOnly) { Invoke-Checked docker @('compose', 'version') }
$vaultDir = Join-Path $repoDir 'data/vault'
if (Test-Path -LiteralPath $vaultDir) {
    throw 'Refusing to overwrite data/vault. Reuse it with docker compose up --build -d --wait.'
}
$envFile = Join-Path $repoDir '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    function New-Secret {
        $bytes = New-Object byte[] 24
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        return -join ($bytes | ForEach-Object { $_.ToString('x2') })
    }
    $lines = @(
        ('DB_PASSWORD=' + (New-Secret)),
        ('MYSQL_ROOT_PASSWORD=' + (New-Secret)),
        ('REDIS_PASSWORD=' + (New-Secret)),
        ('VAULT_WEBHOOK_SECRET=' + (New-Secret)),
        'HOST_BIND=127.0.0.1', 'PORT=8080',
        'SITE_NAME=纸间', 'SITE_DESCRIPTION=用 Obsidian 写作，让想法自然生长。',
        'SITE_AUTHOR=写作者', 'SITE_URL=http://localhost:8080',
        'SITE_GITHUB=', 'SITE_EMAIL=', 'PUBLISH_ROOT=博客', 'CORS_ORIGINS=',
        'SYNC_FETCH_ENABLED=false', 'SYNC_STARTUP_ENABLED=true',
        'SYNC_POLL_ENABLED=true', 'SYNC_POLL_INTERVAL=PT5S', 'SYNC_POLL_INITIAL_DELAY=PT5S'
    )
    $stream = [IO.File]::Open($envFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    $writer = New-Object IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
    try { $writer.Write(($lines -join "`n") + "`n") } finally { $writer.Dispose() }
} else {
    Write-Host 'Keeping existing .env unchanged.'
}
New-Item -ItemType Directory -Path $vaultDir | Out-Null
Get-ChildItem -LiteralPath (Join-Path $repoDir 'example-vault') -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $vaultDir -Recurse
}
Invoke-Checked git @('-C', $vaultDir, 'init', '--quiet')
Invoke-Checked git @('-C', $vaultDir, 'config', 'user.name', 'Blog Demo')
Invoke-Checked git @('-C', $vaultDir, 'config', 'user.email', 'demo@example.invalid')
Invoke-Checked git @('-C', $vaultDir, 'add', '.')
Invoke-Checked git @('-C', $vaultDir, '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Initialize public demo vault')
Write-Host 'Prepared data/vault and .env. Only committed content is published.'
if (-not $PrepareOnly) {
    Invoke-Checked docker @('compose', 'up', '--build', '-d', '--wait')
    Write-Host 'Open http://localhost:8080 (or the PORT configured in .env).'
}
