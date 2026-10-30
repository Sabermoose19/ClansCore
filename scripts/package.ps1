# Run after .\mvnw.cmd clean verify. Does not download or start servers.
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Force dist,dist/developer,examples,test-results|Out-Null
    Copy-Item core/target/ClansCore.jar dist/
    Copy-Item api/target/clans-api-*.jar dist/developer/
    Copy-Item core/src/main/resources/config.yml examples/ClansCore-config.yml
    Copy-Item core/target/surefire-reports/*.txt,core/target/surefire-reports/TEST-*.xml test-results/
    Get-FileHash dist/*.jar -Algorithm SHA256 | ForEach-Object {"$($_.Hash.ToLower())  $([IO.Path]::GetFileName($_.Path))"} | Set-Content dist/SHA256SUMS.txt
    $files=@()
    foreach($directory in @('api/src','core/src','docs','examples','scripts','test-results','.mvn')) {$files+=Get-ChildItem -LiteralPath $directory -File -Recurse -Force}
    foreach($file in @('pom.xml','api/pom.xml','core/pom.xml','README.md','THIRD_PARTY.md','.gitignore','mvnw','mvnw.cmd')) {$files+=Get-Item -LiteralPath $file -Force}
    Add-Type -AssemblyName System.IO.Compression
    $stream=[IO.File]::Open((Join-Path $projectRoot 'dist/ClansPlugins-source.zip'),[IO.FileMode]::Create)
    $zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($file in $files|Sort-Object FullName -Unique) {
            $relative=[IO.Path]::GetRelativePath($projectRoot,$file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Optimal)|Out-Null
        }
    } finally {$zip.Dispose();$stream.Dispose()}
} finally {Pop-Location}
