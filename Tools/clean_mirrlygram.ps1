param(
    [switch]$Auto,
    [switch]$Full,
    [switch]$AfterBuild,
    [switch]$IncludeRootArtifacts,
    [int]$DebugMaxMB = 512,
    [int]$TargetMaxMB = 1536,
    [int]$CxxMaxMB = 4096,
    [int]$StaleDays = 2,
    [int]$CxxStaleDays = 2
)

$ErrorActionPreference = 'Stop'

if (-not $Auto -and -not $Full) {
    $Auto = $true
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path (Join-Path $repoRoot 'TMessagesProj')) -or -not (Test-Path (Join-Path $repoRoot 'mirrlyengine'))) {
    throw "Refusing to run: '$repoRoot' is not the MirrlyGram V2 repository root."
}

$jniLibsDir = (Join-Path $repoRoot 'TMessagesProj\src\main\jniLibs')
$releaseDir = (Join-Path $repoRoot 'release')

function Assert-InsideRepo {
    param([string]$TargetPath)
    $full = [System.IO.Path]::GetFullPath($TargetPath)
    $rootPrefix = $repoRoot.TrimEnd('\') + '\'
    if (-not $full.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to touch path outside MirrlyGram V2: $full"
    }
    $jniLibsPrefix = $jniLibsDir.TrimEnd('\') + '\'
    $releasePrefix = $releaseDir.TrimEnd('\') + '\'
    if ($full.Equals($jniLibsDir, [System.StringComparison]::OrdinalIgnoreCase) -or
        $full.StartsWith($jniLibsPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
        $full.Equals($releaseDir, [System.StringComparison]::OrdinalIgnoreCase) -or
        $full.StartsWith($releasePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to touch protected path: $full"
    }
}

function Get-DirBytes {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return 0L }
    $sum = (Get-ChildItem -LiteralPath $Path -Recurse -Force -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    if ($null -eq $sum) { return 0L }
    return [int64]$sum
}

function Remove-DirSafely {
    param([string]$Path, [ref]$FreedBytes)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    Assert-InsideRepo -TargetPath $Path
    $bytes = Get-DirBytes -Path $Path
    try {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
        $FreedBytes.Value += $bytes
        Write-Host ("[clean] Removed {0} ({1:N1} MB)" -f $Path.Substring($repoRoot.Length + 1), ($bytes / 1MB))
    } catch {
        Write-Warning ("[clean] Skipped locked/in-use path {0}: {1}" -f $Path.Substring($repoRoot.Length + 1), $_.Exception.Message)
    }
}

function Remove-FileSafely {
    param([string]$Path, [ref]$FreedBytes)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    Assert-InsideRepo -TargetPath $Path
    $len = (Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue).Length
    try {
        Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
        $FreedBytes.Value += [int64]$len
        Write-Host ("[clean] Removed {0} ({1:N2} MB)" -f $Path.Substring($repoRoot.Length + 1), ($len / 1MB))
    } catch {
        Write-Warning ("[clean] Skipped locked file {0}: {1}" -f $Path.Substring($repoRoot.Length + 1), $_.Exception.Message)
    }
}

function Test-ActiveMirrlyGramBuild {
    $procs = Get-CimInstance Win32_Process -Filter "Name='cargo.exe' OR Name='rustc.exe' OR Name='clang.exe' OR Name='clang++.exe' OR Name='ninja.exe' OR Name='cmake.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
        $cmd = $p.CommandLine
        if (-not $cmd) { continue }
        if ($cmd -like "*$repoRoot*" -or $cmd -like '*MirrlyGram V2*') {
            if ($p.Name -eq 'java.exe' -and $cmd -like '*GradleDaemon*') {
                continue
            }
            return $true
        }
    }
    return $false
}

if (-not $AfterBuild -and (Test-ActiveMirrlyGramBuild)) {
    Write-Host "[clean] Active MirrlyGram V2 build/compile detected; skipping cleanup."
    exit 0
}

[int64]$freed = 0

if (-not $AfterBuild) {
    $gradle813Daemons = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*gradle-8.13*' -and $_.CommandLine -like '*GradleDaemon*' }
    if ($gradle813Daemons) {
        $gradlew = Join-Path $repoRoot 'gradlew.bat'
        if (Test-Path -LiteralPath $gradlew) {
            Write-Host "[clean] Stopping idle MirrlyGram V2 Gradle 8.13 daemon..."
            & $gradlew --stop | Out-Null
        }
    }
}

$targetDir = Join-Path $repoRoot 'mirrlyengine\target'
$appModules = @('TMessagesProj_App', 'TMessagesProj_AppStandalone', 'TMessagesProj_AppHockeyApp', 'TMessagesProj_AppHuawei', 'TMessagesProj_AppTests')

Get-ChildItem -LiteralPath $repoRoot -File -Filter 'hs_err_pid*.log' -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-FileSafely -Path $_.FullName -FreedBytes ([ref]$freed) }

if ($Full) {
    Remove-DirSafely -Path $targetDir -FreedBytes ([ref]$freed)
    Remove-DirSafely -Path (Join-Path $repoRoot 'TMessagesProj\.cxx') -FreedBytes ([ref]$freed)
    Remove-DirSafely -Path (Join-Path $repoRoot 'TMessagesProj\build') -FreedBytes ([ref]$freed)
    foreach ($m in $appModules) {
        Remove-DirSafely -Path (Join-Path $repoRoot "$m\build") -FreedBytes ([ref]$freed)
    }
    $moduleBuilds = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'TMessagesProj_Modules') -Directory -Recurse -Filter 'build' -ErrorAction SilentlyContinue
    foreach ($mb in $moduleBuilds) {
        Remove-DirSafely -Path $mb.FullName -FreedBytes ([ref]$freed)
    }
    Remove-DirSafely -Path (Join-Path $repoRoot 'buildSrc\build') -FreedBytes ([ref]$freed)
    Remove-DirSafely -Path (Join-Path $repoRoot 'buildSrc\.gradle') -FreedBytes ([ref]$freed)
    Remove-DirSafely -Path (Join-Path $repoRoot 'buildSrc\.kotlin') -FreedBytes ([ref]$freed)
    Remove-DirSafely -Path (Join-Path $repoRoot 'build') -FreedBytes ([ref]$freed)
    if (-not $AfterBuild) {
        Remove-DirSafely -Path (Join-Path $repoRoot '.gradle') -FreedBytes ([ref]$freed)
    }
} else {
    $cutoff = (Get-Date).AddDays(-$StaleDays)
    $cxxCutoff = (Get-Date).AddDays(-$CxxStaleDays)

    if (Test-Path -LiteralPath $targetDir) {
        Remove-DirSafely -Path (Join-Path $targetDir 'tmp') -FreedBytes ([ref]$freed)

        foreach ($hostProfile in @('debug', 'release')) {
            $profileDir = Join-Path $targetDir $hostProfile
            if (-not (Test-Path -LiteralPath $profileDir)) { continue }

            $incDir = Join-Path $profileDir 'incremental'
            $profileBytes = Get-DirBytes -Path $profileDir
            $overCap = ($hostProfile -eq 'debug') -and ($profileBytes -gt ($DebugMaxMB * 1MB))

            if ($AfterBuild -or $overCap) {
                Remove-DirSafely -Path $incDir -FreedBytes ([ref]$freed)
            } elseif (Test-Path -LiteralPath $incDir) {
                Get-ChildItem -LiteralPath $incDir -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.LastWriteTime -lt $cutoff } |
                    ForEach-Object { Remove-DirSafely -Path $_.FullName -FreedBytes ([ref]$freed) }
            }

            $depsDir = Join-Path $profileDir 'deps'
            if (Test-Path -LiteralPath $depsDir) {
                $staleExtensions = @('.pdb', '.exe', '.d', '.rlib', '.rmeta')
                $depsCutoff = if ($AfterBuild) { (Get-Date).AddDays(-1) } else { $cutoff }
                Get-ChildItem -LiteralPath $depsDir -File -ErrorAction SilentlyContinue |
                    Where-Object { ($staleExtensions -contains $_.Extension.ToLowerInvariant()) -and ($_.LastWriteTime -lt $depsCutoff) } |
                    ForEach-Object { Remove-FileSafely -Path $_.FullName -FreedBytes ([ref]$freed) }

                if (($hostProfile -eq 'debug') -and ((Get-DirBytes -Path $profileDir) -gt ($DebugMaxMB * 1MB))) {
                    $candidates = Get-ChildItem -LiteralPath $depsDir -File -ErrorAction SilentlyContinue |
                        Where-Object { $_.Extension.ToLowerInvariant() -in @('.pdb', '.exe', '.rlib', '.rmeta') } |
                        Sort-Object LastWriteTime
                    foreach ($f in $candidates) {
                        if ((Get-DirBytes -Path $profileDir) -le ($DebugMaxMB * 1MB)) { break }
                        Remove-FileSafely -Path $f.FullName -FreedBytes ([ref]$freed)
                    }
                }
            }
        }

        foreach ($dropTarget in @('i686-linux-android', 'x86_64-linux-android')) {
            Remove-DirSafely -Path (Join-Path $targetDir $dropTarget) -FreedBytes ([ref]$freed)
        }

        $androidTargets = @('aarch64-linux-android', 'armv7-linux-androideabi')
        foreach ($abiTarget in $androidTargets) {
            $abiRoot = Join-Path $targetDir $abiTarget
            if (-not (Test-Path -LiteralPath $abiRoot)) { continue }
            Remove-DirSafely -Path (Join-Path $abiRoot 'debug') -FreedBytes ([ref]$freed)
            Remove-DirSafely -Path (Join-Path $abiRoot 'release\incremental') -FreedBytes ([ref]$freed)
            if (-not $AfterBuild -and (Test-Path -LiteralPath $jniLibsDir)) {
                $latest = Get-ChildItem -LiteralPath $abiRoot -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
                if ($null -eq $latest -or $latest.LastWriteTime -lt $cxxCutoff) {
                    Remove-DirSafely -Path $abiRoot -FreedBytes ([ref]$freed)
                }
            }
        }

        if ((Get-DirBytes -Path $targetDir) -gt ($TargetMaxMB * 1MB)) {
            $pruneOrder = @('debug', 'release', 'i686-linux-android', 'x86_64-linux-android', 'armv7-linux-androideabi', 'aarch64-linux-android')
            foreach ($sub in $pruneOrder) {
                if ((Get-DirBytes -Path $targetDir) -le ($TargetMaxMB * 1MB)) { break }
                $subPath = Join-Path $targetDir $sub
                if (-not (Test-Path -LiteralPath $subPath)) { continue }
                if ($AfterBuild -and ($sub -in @('aarch64-linux-android', 'armv7-linux-androideabi'))) { continue }
                Remove-DirSafely -Path $subPath -FreedBytes ([ref]$freed)
            }
        }
    }

    $nativeDupDirs = @(
        'TMessagesProj\build\intermediates\library_jni',
        'TMessagesProj\build\intermediates\merged_native_libs'
    )
    foreach ($m in $appModules) {
        $nativeDupDirs += "$m\build\intermediates\native_debug_metadata"
        $nativeDupDirs += "$m\build\intermediates\merged_native_libs"
        $nativeDupDirs += "$m\build\intermediates\stripped_native_libs"
    }

    foreach ($rel in $nativeDupDirs) {
        $p = Join-Path $repoRoot $rel
        if (-not (Test-Path -LiteralPath $p)) { continue }
        if ($AfterBuild) {
            Remove-DirSafely -Path $p -FreedBytes ([ref]$freed)
        } else {
            $latest = Get-ChildItem -LiteralPath $p -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($null -eq $latest -or $latest.LastWriteTime -lt $cutoff) {
                Remove-DirSafely -Path $p -FreedBytes ([ref]$freed)
            }
        }
    }

    # Keep only the latest RelWithDebInfo C++ cache for active ARM ABIs across both .cxx and intermediates/cxx.
    $keepCxxAbis = @('arm64-v8a', 'armeabi-v7a')
    $cxxRoot = Join-Path $repoRoot 'TMessagesProj\.cxx'
    $intermCxxRoot = Join-Path $repoRoot 'TMessagesProj\build\intermediates\cxx'
    $latestCxxHash = $null

    if (Test-Path -LiteralPath $cxxRoot) {
        Get-ChildItem -LiteralPath $cxxRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.Name -notin @('RelWithDebInfo', 'tools')) {
                Remove-DirSafely -Path $_.FullName -FreedBytes ([ref]$freed)
            }
        }

        $cxxRelDir = Join-Path $cxxRoot 'RelWithDebInfo'
        if (Test-Path -LiteralPath $cxxRelDir) {
            $hashEntries = @(Get-ChildItem -LiteralPath $cxxRelDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $newest = Get-ChildItem -LiteralPath $_.FullName -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
                [PSCustomObject]@{
                    Name   = $_.Name
                    Path   = $_.FullName
                    Newest = if ($newest) { $newest.LastWriteTime } else { $_.LastWriteTime }
                }
            } | Sort-Object Newest -Descending)

            if ($hashEntries.Count -gt 0) {
                $latestCxxHash = $hashEntries[0].Name
                for ($i = 1; $i -lt $hashEntries.Count; $i++) {
                    Remove-DirSafely -Path $hashEntries[$i].Path -FreedBytes ([ref]$freed)
                }

                Get-ChildItem -LiteralPath $hashEntries[0].Path -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                    if ($keepCxxAbis -notcontains $_.Name) {
                        Remove-DirSafely -Path $_.FullName -FreedBytes ([ref]$freed)
                    }
                }
            }
        }
    }

    if (Test-Path -LiteralPath $intermCxxRoot) {
        Get-ChildItem -LiteralPath $intermCxxRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.Name -ne 'RelWithDebInfo') {
                Remove-DirSafely -Path $_.FullName -FreedBytes ([ref]$freed)
            }
        }

        $intermRelDir = Join-Path $intermCxxRoot 'RelWithDebInfo'
        if (Test-Path -LiteralPath $intermRelDir) {
            $intermEntries = @(Get-ChildItem -LiteralPath $intermRelDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $newest = Get-ChildItem -LiteralPath $_.FullName -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
                [PSCustomObject]@{
                    Name   = $_.Name
                    Path   = $_.FullName
                    Newest = if ($newest) { $newest.LastWriteTime } else { $_.LastWriteTime }
                }
            } | Sort-Object Newest -Descending)

            if ($intermEntries.Count -gt 0) {
                $keepIntermHash = if ($latestCxxHash -and ($intermEntries.Name -contains $latestCxxHash)) { $latestCxxHash } else { $intermEntries[0].Name }
                foreach ($entry in $intermEntries) {
                    if ($entry.Name -ne $keepIntermHash) {
                        Remove-DirSafely -Path $entry.Path -FreedBytes ([ref]$freed)
                    } else {
                        $objDir = Join-Path $entry.Path 'obj'
                        if (Test-Path -LiteralPath $objDir) {
                            Get-ChildItem -LiteralPath $objDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                                if ($keepCxxAbis -notcontains $_.Name) {
                                    Remove-DirSafely -Path $_.FullName -FreedBytes ([ref]$freed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (-not $AfterBuild -and $latestCxxHash) {
        $totalCxxBytes = (Get-DirBytes -Path $cxxRoot) + (Get-DirBytes -Path $intermCxxRoot)
        if ($totalCxxBytes -gt ($CxxMaxMB * 1MB)) {
            $v7Cxx = Join-Path $cxxRoot "RelWithDebInfo\$latestCxxHash\armeabi-v7a"
            Remove-DirSafely -Path $v7Cxx -FreedBytes ([ref]$freed)
            $v7Obj = Join-Path $intermCxxRoot "RelWithDebInfo\$latestCxxHash\obj\armeabi-v7a"
            Remove-DirSafely -Path $v7Obj -FreedBytes ([ref]$freed)
        }
    }

    if (-not $AfterBuild) {
        foreach ($rel in @('build\reports', 'TMessagesProj\build\tmp', 'TMessagesProj_App\build\outputs\mapping')) {
            $p = Join-Path $repoRoot $rel
            if (Test-Path -LiteralPath $p) {
                $newest = Get-ChildItem -LiteralPath $p -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
                if ($null -eq $newest -or $newest.LastWriteTime -lt $cutoff) {
                    Remove-DirSafely -Path $p -FreedBytes ([ref]$freed)
                }
            }
        }

        $appApkOut = Join-Path $repoRoot 'TMessagesProj_App\build\outputs\apk'
        $hasReleaseApk = (Get-ChildItem -LiteralPath $releaseDir -File -Filter 'MirrlyGram-*.apk' -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0
        if ($hasReleaseApk -and (Test-Path -LiteralPath $appApkOut)) {
            $newestApk = Get-ChildItem -LiteralPath $appApkOut -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($null -eq $newestApk -or $newestApk.LastWriteTime -lt $cutoff) {
                Remove-DirSafely -Path $appApkOut -FreedBytes ([ref]$freed)
            }
        }

        $execHistDir = Join-Path $repoRoot '.gradle\8.13\executionHistory'
        $execHistBin = Join-Path $execHistDir 'executionHistory.bin'
        if ((Test-Path -LiteralPath $execHistBin) -and ((Get-Item -LiteralPath $execHistBin -Force).Length -gt 50MB)) {
            Remove-DirSafely -Path $execHistDir -FreedBytes ([ref]$freed)
        }
    }
}

if ($IncludeRootArtifacts) {
    Get-ChildItem -LiteralPath $repoRoot -File -Filter 'MirrlyGram-*.apk' -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-FileSafely -Path $_.FullName -FreedBytes ([ref]$freed) }
    foreach ($shot in @('current_screen.png', 'mirrlygram_screen.png', 'mirrly_settings.png', 'telegram_sent_verify4.png', 'telegram_delivery_verify.png')) {
        Remove-FileSafely -Path (Join-Path $repoRoot $shot) -FreedBytes ([ref]$freed)
    }
}

Write-Host ("[clean] Total freed inside MirrlyGram V2: {0:N2} GB ({1:N1} MB)" -f ($freed / 1GB), ($freed / 1MB))
