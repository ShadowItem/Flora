$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$InputDir = Join-Path $ProjectRoot 'target\jpackage-input'
$DistDir = Join-Path $ProjectRoot 'dist'
$JarName = 'hive-agent-ui-1.0.0.jar'

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Run this script on Windows: jpackage builds a native Windows launcher only on Windows.'
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'Apache Maven is required and must be available on PATH.'
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw 'A JDK with jpackage (JDK 17 or newer) is required and must be available on PATH.'
}

Push-Location $ProjectRoot
try {
    & mvn -q clean package dependency:copy-dependencies `
        '-DincludeScope=runtime' `
        "-DoutputDirectory=$InputDir"
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

    Copy-Item (Join-Path $ProjectRoot "target\$JarName") $InputDir -Force
    if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }

    & jpackage `
        --type app-image `
        --name Flora `
        --app-version 1.0.0 `
        --input $InputDir `
        --main-jar $JarName `
        --main-class ru.digitalhive.ui.Launcher `
        --description 'Локальный помощник пчеловода' `
        --dest $DistDir
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed to create the Windows application image.' }

    $Exe = Join-Path $DistDir 'Flora\Flora.exe'
    if (-not (Test-Path $Exe)) { throw "Expected launcher was not created: $Exe" }

    $PortableZip = Join-Path $DistDir 'Flora-portable-Windows.zip'
    Compress-Archive -Path (Join-Path $DistDir 'Flora') -DestinationPath $PortableZip -Force
    Write-Host "Portable app image created: $Exe"
    Write-Host "Keep the whole Flora folder together; the EXE uses its adjacent app and runtime folders."
    Write-Host "Portable package: $PortableZip"
}
finally {
    Pop-Location
}
