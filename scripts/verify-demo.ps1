[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
function Get-Api {
    param([string]$Path)
    $raw = & docker compose exec -T app curl -fsS ('http://localhost:8080' + $Path)
    if ($LASTEXITCODE -ne 0) { throw "Request failed: $Path" }
    return ($raw -join "`n" | ConvertFrom-Json)
}
if ((Get-Api '/actuator/health').status -ne 'UP') { throw 'Health is not UP' }
if (-not (Get-Api '/api/v1/site').navigation) { throw 'Missing navigation' }
if (-not (Get-Api '/api/v1/posts/start-with-a-note').bodyHtml) { throw 'Missing article body' }
if ((Get-Api '/api/v1/posts/code-and-tables').bodyHtml -notmatch '<table>') { throw 'Missing table rendering' }
if ((Get-Api '/api/v1/posts/a-long-title').tags.Count -ne 0) { throw 'Unexpected tags' }
foreach ($slug in @('unpublished-example', 'excluded-draft', 'private-example', 'not-a-real-post')) {
    $status = & docker compose exec -T app curl -sS -o /dev/null -w '%{http_code}' ('http://localhost:8080/api/v1/posts/' + $slug)
    if ($LASTEXITCODE -ne 0 -or $status -ne '404') { throw "Expected 404 for $slug, got $status" }
}
$status = & docker compose exec -T app curl -sS -o /dev/null -w '%{http_code}' 'http://localhost:8080/api/v1/posts?page=-1'
if ($LASTEXITCODE -ne 0 -or $status -ne '400') { throw 'Invalid pagination did not return 400' }
foreach ($path in @('/', '/archive', '/notes', '/lab', '/about', '/p/images-and-space')) {
    & docker compose exec -T app curl -fsS -o /dev/null ('http://localhost:8080' + $path)
    if ($LASTEXITCODE -ne 0) { throw "Page failed: $path" }
}
Write-Host 'Demo health, pages, API, formatting, pagination and publication exclusions passed.'
