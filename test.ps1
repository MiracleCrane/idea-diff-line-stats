param(
    [Parameter(Mandatory=$true)][string]$IdeaHome,
    [string]$BuildDirectory = (Join-Path $PSScriptRoot 'build'),
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1') -IdeaHome $IdeaHome -BuildDirectory $BuildDirectory
$javaBin = Join-Path $IdeaHome 'jbr\bin'
$testClasses = Join-Path $BuildDirectory 'test-classes'
$pluginLib = Join-Path $BuildDirectory 'package\diff-line-stats\lib\*'
$classPath = (Join-Path $IdeaHome 'lib\*') + ';' + (Join-Path $IdeaHome 'plugins\vcs-git\lib\*') + ';' + $pluginLib
$tests = @((Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\test\java') -Recurse -Filter '*.java').FullName)
& (Join-Path $javaBin 'javac.exe') --release 21 -encoding UTF-8 -classpath $classPath -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& (Join-Path $javaBin 'java.exe') -classpath ($classPath + ';' + $testClasses) dev.miraclecrane.diffstats.StatsCalculatorTest
if ($LASTEXITCODE -ne 0) { throw 'Text regression failed' }
& $Python (Join-Path $PSScriptRoot 'src\test\prepare_integration.py') --idea-home $IdeaHome --build-dir $BuildDirectory
if ($LASTEXITCODE -ne 0) { throw 'Test environment preparation failed' }
& (Join-Path $javaBin 'java.exe') ('@' + (Join-Path $BuildDirectory 'idea-test.args'))
if ($LASTEXITCODE -ne 0) { throw 'IDE integration failed; inspect sandbox\log\idea.log' }
