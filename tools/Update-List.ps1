<#
  Update-List.ps1
  Заменяет содержимое папки репозитория новой версией проекта из zip-архива.

  Запуск (PowerShell):
      powershell -ExecutionPolicy Bypass -File .\Update-List.ps1

  Папка репозитория берётся так: если вы запускаете скрипт из папки с .git, это она, иначе D:\my-projects\list.
  Архив ищется в текущей папке, рядом со скриптом, в «Загрузках» и на Рабочем столе (берётся самый новый).
  Свои пути:
      powershell -ExecutionPolicy Bypass -File .\Update-List.ps1 -Repo "D:\my-projects\list" -Zip "C:\Users\Имя\Downloads\List-android-project.zip"

  Что делает скрипт, по порядку:
    1. Проверяет, что папка репозитория настоящая (есть .git), а архив открывается и содержит проект.
    2. Распаковывает архив во временную папку, а НЕ в репозиторий, и проверяет, что всё на месте.
    3. Копирует текущую папку в папку .backups внутри репозитория: .backups\list-<дата> (в git она не попадает,
       хранятся 5 последних копий, число меняется ключом -Keep; совсем без копии: -NoBackup).
    4. Только после этого приводит папку репозитория к виду архива. Папки .git, .gradle, .idea, build, .backups
       и файл local.properties остаются нетронутыми.
    5. Сверяет результат и показывает, сколько файлов изменилось с точки зрения git.
  Скрипт ничего не коммитит и ничего не отправляет в GitHub. Если проверка не прошла, ваша папка не меняется.
#>
param(
    [string]$Zip = "",
    [string]$Repo = "",
    [switch]$NoBackup,
    [int]$Keep = 5
)

$ErrorActionPreference = "Stop"
$script:changed = $false
$script:tmp = $null

function Say([string]$text, [string]$color = "Gray") {
    Write-Host $text -ForegroundColor $color
}

# Запускает git и возвращает его вывод текстом; ошибка git не прерывает скрипт.
function Invoke-GitText([string[]]$gitArgs) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $text = (& git @gitArgs 2>&1 | Out-String)
    } catch {
        $text = ""
    } finally {
        $ErrorActionPreference = $old
    }
    return $text.Trim()
}

function Find-Zip([string]$given) {
    if (-not [string]::IsNullOrWhiteSpace($given)) {
        if (-not (Test-Path -LiteralPath $given -PathType Leaf)) {
            throw ("Архив не найден: " + $given)
        }
        return (Resolve-Path -LiteralPath $given).Path
    }
    $places = @((Get-Location).Path)
    if ($PSScriptRoot) { $places += $PSScriptRoot }
    $places += (Join-Path $env:USERPROFILE "Downloads")
    $places += (Join-Path $env:USERPROFILE "Desktop")
    $found = @()
    foreach ($p in $places) {
        if (Test-Path -LiteralPath $p -PathType Container) {
            $found += @(Get-ChildItem -LiteralPath $p -Filter "List-android-project*.zip" -File -ErrorAction SilentlyContinue)
        }
    }
    if ($found.Count -eq 0) {
        throw "Не нашёл List-android-project.zip ни в текущей папке, ни в Загрузках, ни на Рабочем столе. Положите архив в Загрузки или укажите путь: -Zip ""C:\путь\List-android-project.zip"""
    }
    return ($found | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

function Invoke-Update {
    # 1. Репозиторий
    $repoGiven = $Repo
    if ([string]::IsNullOrWhiteSpace($repoGiven)) {
        if (Test-Path -LiteralPath (Join-Path (Get-Location).Path ".git")) {
            $repoGiven = (Get-Location).Path
        } else {
            $repoGiven = "D:\my-projects\list"
        }
    }
    if (-not (Test-Path -LiteralPath $repoGiven -PathType Container)) {
        throw ("Папка репозитория не найдена: " + $repoGiven + ". Если репозиторий лежит в другом месте, добавьте -Repo ""D:\путь""")
    }
    $repoPath = (Resolve-Path -LiteralPath $repoGiven).Path.TrimEnd("\")
    if ($repoPath.Length -le 3) {
        throw ("Указан корень диска (" + $repoPath + "). Нужна папка самого репозитория.")
    }
    if (-not (Test-Path -LiteralPath (Join-Path $repoPath ".git"))) {
        throw ("В папке " + $repoPath + " нет .git, значит, это не репозиторий. Проверьте путь.")
    }
    Say ("Репозиторий:     " + $repoPath) White

    $remote = ""
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $remote = Invoke-GitText @("-C", $repoPath, "remote", "get-url", "origin")
        if ($remote -match "^(fatal|error)") { $remote = "" }
    }
    if ($remote) {
        Say ("Адрес на GitHub: " + $remote) White
    } else {
        Say "Адрес на GitHub узнать не удалось (git не найден или у репозитория нет origin)." Yellow
    }
    if ($remote -notmatch "list-viewer") {
        Say "Убедитесь, что это нужная папка: адрес не похож на list-viewer." Yellow
        $answer = Read-Host "Продолжить? Введите y и нажмите Enter (любой другой ответ отменит)"
        if ($answer -ne "y") { throw "Отменено по вашему ответу." }
    }

    # 2. Архив
    $zipPath = Find-Zip $Zip
    $zipInfo = Get-Item -LiteralPath $zipPath
    Say ("Архив:           " + $zipPath + "  (" + [math]::Round($zipInfo.Length / 1MB, 1) + " МБ, изменён " + $zipInfo.LastWriteTime.ToString("dd.MM.yyyy HH:mm") + ")") White

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $script:tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("list-update-" + [guid]::NewGuid().ToString("N"))
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $script:tmp)
    } catch {
        throw ("Не удалось распаковать архив (возможно, он скачался не полностью). " + $_.Exception.Message)
    }

    $src = $null
    foreach ($candidate in @($script:tmp, (Join-Path $script:tmp "List"))) {
        if (Test-Path -LiteralPath (Join-Path $candidate "gradlew")) { $src = $candidate; break }
    }
    if ($null -eq $src) {
        throw "В архиве нет проекта (не нашёл файл gradlew). Похоже, это не тот архив."
    }
    $required = @(
        "settings.gradle.kts",
        "build.gradle.kts",
        "app\build.gradle.kts",
        "app\src\main\AndroidManifest.xml",
        "app\src\main\java\app\fileviewer\MainActivity.kt",
        "app\src\main\java\app\fileviewer\HomeParts.kt",
        "app\src\main\java\app\fileviewer\Backup.kt",
        ".github\workflows\build.yml",
        "README.md"
    )
    foreach ($r in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $src $r))) {
            throw ("В архиве нет файла " + $r + ". Архив неполный или это старая версия, ничего не заменено.")
        }
    }
    $srcFiles = @(Get-ChildItem -LiteralPath $src -Recurse -File -Force)
    if ($srcFiles.Count -lt 60) {
        throw ("В архиве слишком мало файлов (" + $srcFiles.Count + "). Архив неполный, ничего не заменено.")
    }
    Say ("Архив проверен, файлов в проекте: " + $srcFiles.Count) Green

    # 3. Резервная копия: папка .backups внутри репозитория (она исключена из git и из замены)
    $backup = $null
    if (-not $NoBackup) {
        $backupRoot = Join-Path $repoPath ".backups"
        $backup = Join-Path $backupRoot ("list-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
        Say "Делаю резервную копию текущей папки..." Gray
        & robocopy $repoPath $backup /E /XD ".git" ".gradle" ".idea" "build" ".backups" /NFL /NDL /NJH /NJS /NP | Out-Null
        if ($LASTEXITCODE -ge 8) {
            throw ("Не удалось сделать резервную копию (код " + $LASTEXITCODE + "). Ничего не заменено.")
        }
        Say ("Резервная копия: " + $backup) Green
        # Старые копии не копятся без конца: остаются последние (по умолчанию 5).
        $limit = [Math]::Max($Keep, 1)
        $old = @(Get-ChildItem -LiteralPath $backupRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^list-\d{8}-\d{6}$' } |
            Sort-Object Name -Descending |
            Select-Object -Skip $limit)
        foreach ($d in $old) {
            Remove-Item -LiteralPath $d.FullName -Recurse -Force -ErrorAction SilentlyContinue
            Say ("Удалена старая копия: " + $d.Name) Gray
        }
    }

    # 4. Замена: папка репозитория приводится к виду архива (лишние старые файлы уходят)
    $script:changed = $true
    Say "Заменяю файлы проекта..." Gray
    & robocopy $src $repoPath /MIR /XD ".git" ".gradle" ".idea" "build" ".backups" /XF "local.properties" /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw ("Копирование не удалось (код " + $LASTEXITCODE + "). Старая версия лежит в резервной копии: " + $backup)
    }

    # 5. Сверка: robocopy в режиме "только список" (/L) ничего не меняет и возвращает 0, если папки совпали
    & robocopy $src $repoPath /MIR /L /XD ".git" ".gradle" ".idea" "build" ".backups" /XF "local.properties" /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw ("После копирования папка не совпала с архивом (код сверки " + $LASTEXITCODE + "). Старая версия лежит в резервной копии: " + $backup)
    }
    foreach ($r in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $repoPath $r))) {
            throw ("После копирования в папке нет файла " + $r + ". Старая версия лежит в резервной копии: " + $backup)
        }
    }

    Say ""
    Say "Готово: папка приведена к новой версии." Green
    if ($backup) {
        Say ("Старая версия сохранена в " + $backup + ". Папка .backups в git не попадает; когда убедитесь, что всё в порядке, копии можно удалить.") Gray
    }
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $status = Invoke-GitText @("-C", $repoPath, "status", "--short")
        $lines = @($status -split "`r?`n" | Where-Object { $_ -ne "" })
        Say ("Файлов, изменённых с точки зрения git: " + $lines.Count) Cyan
        foreach ($line in ($lines | Select-Object -First 30)) { Say ("  " + $line) Gray }
        if ($lines.Count -gt 30) { Say ("  ... и ещё " + ($lines.Count - 30)) Gray }
    }
    Say ""
    Say "Дальше: проверьте список изменений, затем git add -A, git commit и git push." Cyan
}

try {
    Invoke-Update
} catch {
    Write-Host ""
    Write-Host ("ОСТАНОВЛЕНО: " + $_.Exception.Message) -ForegroundColor Red
    if (-not $script:changed) {
        Write-Host "Файлы репозитория не тронуты." -ForegroundColor Yellow
    }
} finally {
    if ($script:tmp -and (Test-Path -LiteralPath $script:tmp)) {
        Remove-Item -LiteralPath $script:tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
