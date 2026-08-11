param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$')]
    [string]$Version,
    [string]$MavenCommand = 'mvn'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$appPom = Join-Path $repoRoot 'app\pom.xml'
$bomPom = Join-Path $repoRoot 'app\bom\pom.xml'

[xml]$bom = Get-Content -LiteralPath $bomPom -Raw -Encoding UTF8
$tychoVersionNode = $bom.SelectSingleNode(
    "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='tycho.version']")
if ($null -eq $tychoVersionNode)
{
    throw "Не удалось определить версию Tycho из $bomPom."
}

$maven = Get-Command $MavenCommand -ErrorAction Stop
$tychoVersion = $tychoVersionNode.InnerText.Trim()
$setVersionGoal = "org.eclipse.tycho:tycho-versions-plugin:${tychoVersion}:set-version"

& $maven.Source -f $appPom $setVersionGoal "-DnewVersion=$Version" `
    '-Dmodules=bom' '-Dartifacts=parent,bom'
if ($LASTEXITCODE -ne 0)
{
    throw "Tycho Versions Plugin завершился с кодом $LASTEXITCODE."
}

Write-Host "Версия проекта изменена на $Version."
