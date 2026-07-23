param(
    [string]$MavenCommand = 'mvn',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$appPom = Join-Path $repoRoot 'app\pom.xml'
$changelog = Join-Path $repoRoot 'docs\CHANGELOG.md'
$bundleTarget = Join-Path $repoRoot 'app\bundles\edt.metadata.favorites\target'
$repositoryTarget = Join-Path $repoRoot 'app\repositories\edt.metadata.favorites.repository\target'
$buildRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot '.build\releases'))

[xml]$pom = Get-Content -LiteralPath $appPom -Raw -Encoding UTF8
$versionNode = $pom.SelectSingleNode(
    "/*[local-name()='project']/*[local-name()='version']")
if ($null -eq $versionNode)
{
    throw "Не удалось определить версию проекта из $appPom."
}

$version = $versionNode.InnerText.Trim()
if ($version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$')
{
    throw "Версия '$version' не может использоваться в имени каталога release."
}
if ($version.EndsWith('-SNAPSHOT', [StringComparison]::OrdinalIgnoreCase))
{
    Write-Warning "Собирается SNAPSHOT-версия $version. Перед публикацией GitHub Release задайте release-версию."
}

if (-not $SkipBuild)
{
    $java = Get-Command java -ErrorAction Stop
    $maven = Get-Command $MavenCommand -ErrorAction Stop

    $javaVersion = (& $java.Source -version 2>&1 | Select-Object -First 1)
    if ($javaVersion -notmatch '"(\d+)')
    {
        throw 'Не удалось определить версию Java.'
    }
    if ([int]$Matches[1] -lt 21)
    {
        throw "Для Tycho требуется JDK 21 или новее. Активная версия: $($Matches[1])."
    }

    & $maven.Source -f $appPom clean verify
    if ($LASTEXITCODE -ne 0)
    {
        throw "Maven завершился с кодом $LASTEXITCODE."
    }
}

$bundleJar = Get-ChildItem -LiteralPath $bundleTarget -Filter 'edt.metadata.favorites-*.jar' -File |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
$repositoryZip = Get-ChildItem -LiteralPath $repositoryTarget -Filter '*.zip' -File |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if ($null -eq $bundleJar)
{
    throw "Не найден bundle JAR в $bundleTarget."
}
if ($null -eq $repositoryZip)
{
    throw "Не найден update-site ZIP в $repositoryTarget."
}

$releaseDir = [IO.Path]::GetFullPath((Join-Path $buildRoot $version))
if (-not $releaseDir.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar,
    [StringComparison]::OrdinalIgnoreCase))
{
    throw "Недопустимый путь release: $releaseDir."
}

if (Test-Path -LiteralPath $releaseDir)
{
    Remove-Item -LiteralPath $releaseDir -Recurse -Force
}
New-Item -ItemType Directory -Path $releaseDir | Out-Null

$bundleName = "edt-metadata-favorites-$version.jar"
$updateSiteName = "edt-metadata-favorites-update-site-$version.zip"
$bundlePath = Join-Path $releaseDir $bundleName
$updateSitePath = Join-Path $releaseDir $updateSiteName

Copy-Item -LiteralPath $bundleJar.FullName -Destination $bundlePath
Copy-Item -LiteralPath $repositoryZip.FullName -Destination $updateSitePath

$changelogLines = Get-Content -LiteralPath $changelog -Encoding UTF8
$unreleasedStart = -1
for ($index = 0; $index -lt $changelogLines.Count; $index++)
{
    if ($changelogLines[$index] -match '^## \[Unreleased\]\s*$')
    {
        $unreleasedStart = $index + 1
        break
    }
}
if ($unreleasedStart -lt 0)
{
    throw "В $changelog не найдена секция '## [Unreleased]'."
}

$changes = [Collections.Generic.List[string]]::new()
for ($index = $unreleasedStart; $index -lt $changelogLines.Count; $index++)
{
    if ($changelogLines[$index] -match '^## \[')
    {
        break
    }
    $changes.Add($changelogLines[$index])
}
while ($changes.Count -gt 0 -and [string]::IsNullOrWhiteSpace($changes[0]))
{
    $changes.RemoveAt(0)
}
while ($changes.Count -gt 0 -and [string]::IsNullOrWhiteSpace($changes[$changes.Count - 1]))
{
    $changes.RemoveAt($changes.Count - 1)
}
if ($changes.Count -eq 0)
{
    throw "Секция Unreleased в $changelog пуста."
}

$releaseNotes = @(
    "# EDT Metadata Favorites $version"
    ''
    '## Changes'
    ''
) + $changes + @(
    ''
    '## Artifacts'
    ''
    "- ``$updateSiteName`` — архив p2 update site для установки через Install New Software."
    "- ``$bundleName`` — bundle JAR для установки через dropins."
)
$releaseNotesPath = Join-Path $releaseDir 'release-notes.md'
Set-Content -LiteralPath $releaseNotesPath -Value $releaseNotes -Encoding UTF8

$checksums = @($bundlePath, $updateSitePath) | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($_))"
}
Set-Content -LiteralPath (Join-Path $releaseDir 'SHA256SUMS.txt') -Value $checksums -Encoding ASCII

Write-Host "Release package: $releaseDir"
Get-ChildItem -LiteralPath $releaseDir | Select-Object Name, Length

