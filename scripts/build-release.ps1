param(
    [string]$MavenCommand = 'mvn',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
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
    $versionNode = $pom.SelectSingleNode(
        "/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']")
}
if ($null -eq $versionNode)
{
    throw "Не удалось определить собственную или унаследованную версию проекта из $appPom."
}

$version = $versionNode.InnerText.Trim()
if ($version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$')
{
    throw "Версия '$version' не может использоваться в имени каталога release."
}
$isSnapshot = $version.EndsWith('-SNAPSHOT', [StringComparison]::OrdinalIgnoreCase)
if ($isSnapshot)
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

$bundleJarPath = Join-Path $bundleTarget "edt.metadata.favorites-$version.jar"
$bundleJar = Get-Item -LiteralPath $bundleJarPath -ErrorAction SilentlyContinue
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

Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundleArchive = [IO.Compression.ZipFile]::OpenRead($bundleJar.FullName)
try
{
    $manifestEntry = $bundleArchive.GetEntry('META-INF/MANIFEST.MF')
    if ($null -eq $manifestEntry)
    {
        throw "Bundle JAR не содержит META-INF/MANIFEST.MF: $($bundleJar.FullName)."
    }
    $manifestReader = [IO.StreamReader]::new($manifestEntry.Open())
    try
    {
        $manifest = $manifestReader.ReadToEnd()
    }
    finally
    {
        $manifestReader.Dispose()
    }
}
finally
{
    $bundleArchive.Dispose()
}
$bundleVersionMatch = [Regex]::Match($manifest, '(?m)^Bundle-Version:\s*([^\r\n]+)')
if (-not $bundleVersionMatch.Success)
{
    throw "Bundle JAR не содержит Bundle-Version: $($bundleJar.FullName)."
}
$bundleVersion = $bundleVersionMatch.Groups[1].Value.Trim()
$expectedPluginEntry = "plugins/edt.metadata.favorites_${bundleVersion}.jar"

$archive = [IO.Compression.ZipFile]::OpenRead($repositoryZip.FullName)
try
{
    $matchingPlugin = $archive.GetEntry($expectedPluginEntry)
}
finally
{
    $archive.Dispose()
}
if ($null -eq $matchingPlugin)
{
    throw "Update-site ZIP не содержит ${expectedPluginEntry}: $($repositoryZip.FullName)."
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
$updateSiteName = "edt-metadata-favorites-$version.zip"
$bundlePath = Join-Path $releaseDir $bundleName
$updateSitePath = Join-Path $releaseDir $updateSiteName

Copy-Item -LiteralPath $bundleJar.FullName -Destination $bundlePath
Copy-Item -LiteralPath $repositoryZip.FullName -Destination $updateSitePath

$changelogLines = Get-Content -LiteralPath $changelog -Encoding UTF8
$changesStart = -1
$changesSection = '[Unreleased]'
$versionHeading = "## [$version]"
for ($index = 0; $index -lt $changelogLines.Count; $index++)
{
    if ($changelogLines[$index] -match '^## \[Unreleased\]\s*$')
    {
        $changesStart = $index + 1
        break
    }
}
if ($changesStart -lt 0)
{
    for ($index = 0; $index -lt $changelogLines.Count; $index++)
    {
        if ($changelogLines[$index] -eq $versionHeading)
        {
            $changesStart = $index + 1
            $changesSection = "[$version]"
            break
        }
    }
}
if ($changesStart -lt 0 -and $isSnapshot)
{
    $changesStart = $changelogLines.Count
    $changesSection = 'SNAPSHOT'
}
elseif ($changesStart -lt 0)
{
    throw "В $changelog не найдены секции '## [Unreleased]' и '$versionHeading'."
}

$changes = [Collections.Generic.List[string]]::new()
for ($index = $changesStart; $index -lt $changelogLines.Count; $index++)
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
    if ($isSnapshot)
    {
        $changes.Add('Тестовая SNAPSHOT-сборка текущего состояния ветки.')
    }
    else
    {
        throw "Секция $changesSection в $changelog пуста."
    }
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
