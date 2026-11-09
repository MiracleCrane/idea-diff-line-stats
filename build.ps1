param(
    [Parameter(Mandatory=$true)][string]$IdeaHome,
    [string]$BuildDirectory = (Join-Path $PSScriptRoot 'build')
)
$ErrorActionPreference = 'Stop'
$product = Get-Content -LiteralPath (Join-Path $IdeaHome 'product-info.json') -Raw | ConvertFrom-Json
if ($product.buildNumber -notlike '263.*') { throw 'This plugin targets IntelliJ Platform 263 (2026.3).' }
$javaBin = Join-Path $IdeaHome 'jbr\bin'
$classes = Join-Path $BuildDirectory ('classes-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
# Platform 263 ships the VCS API and implementation in a separate bundled plugin.
$classPath = (Join-Path $IdeaHome 'lib\*') + ';' + (Join-Path $IdeaHome 'plugins\platform-vcs-plugin\lib\*') + ';' + (Join-Path $IdeaHome 'plugins\platform-vcs-plugin\lib\modules\*')
& (Join-Path $javaBin 'javac.exe') --release 21 -encoding UTF-8 -classpath $classPath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\META-INF') -Destination $classes -Recurse -Force
$pluginRoot = Join-Path $BuildDirectory 'package\diff-line-stats'
$libDirectory = Join-Path $pluginRoot 'lib'
New-Item -ItemType Directory -Force -Path $libDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'LICENSE') -Destination (Join-Path $pluginRoot 'LICENSE') -Force
$jarFile = Join-Path $libDirectory 'diff-line-stats.jar'
Add-Type -AssemblyName System.IO.Compression
$jarStream = [IO.File]::Create($jarFile)
$jar = [IO.Compression.ZipArchive]::new($jarStream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $classes -Recurse -File) {
        $entryName = [IO.Path]::GetRelativePath($classes, $file.FullName).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($jar, $file.FullName, $entryName) | Out-Null
    }
} finally { $jar.Dispose(); $jarStream.Dispose() }
[xml]$descriptor = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\META-INF\plugin.xml') -Raw
$version = $descriptor.'idea-plugin'.version
$zip = Join-Path $BuildDirectory "diff-line-stats-$version.zip"
Compress-Archive -LiteralPath $pluginRoot -DestinationPath $zip -Force
Write-Output "Built: $zip (IDEA $($product.version), $($product.buildNumber))"
