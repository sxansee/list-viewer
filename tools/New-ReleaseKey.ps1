<#
  New-ReleaseKey.ps1
  Создаёт собственный ключ подписи для List и помогает передать его GitHub (в секреты репозитория).

  Запуск (PowerShell):
      powershell -ExecutionPolicy Bypass -File .\New-ReleaseKey.ps1

  Зачем это нужно. Сейчас каждая сборка подписана общедоступным ключом из репозитория (app/debug.keystore):
  обновление с такой подписью может собрать кто угодно. Собственный ключ есть только у вас, и обновить приложение
  сможете только вы. Платой служит один переход: Android не ставит приложение с другой подписью поверх старого,
  поэтому старую версию нужно один раз удалить и поставить новую (список переносится резервной копией в самом
  приложении; шаги описаны в README, раздел «Свой ключ подписи»). Переходить необязательно: без своего ключа
  всё продолжает работать, как раньше.

  Что делает скрипт, по порядку:
    1. Находит keytool (он входит в Java JDK). Если Java нет, подсказывает, как её поставить.
    2. Создаёт ключ list-release.p12 (RSA 3072, срок 100 лет) и случайный пароль в папке ВНЕ репозитория
       (по умолчанию «Документы\List-signing-key»). Уже существующий ключ скрипт не перезаписывает никогда.
    3. Проверяет, что ключ открывается, и показывает отпечаток сертификата: он же будет написан в «О приложении».
    4. Помогает записать на GitHub два секрета, KEYSTORE_BASE64 и KEYSTORE_PASSWORD: каждое значение кладётся
       в буфер обмена, вам остаётся вставить его в форму на сайте.
  Скрипт ничего не отправляет в интернет. Ключ и пароль храните в надёжном месте (менеджер паролей, личное облако,
  копия на флешке) и не кладите в репозиторий: без ключа приложение, подписанное им, больше не обновить.

  Свой путь для файлов ключа:
      powershell -ExecutionPolicy Bypass -File .\New-ReleaseKey.ps1 -Out "D:\List-key"
#>
param(
    [string]$Out = "",
    [string]$Repo = "sxansee/list-viewer"
)

$ErrorActionPreference = "Stop"
$Alias = "list"

function Say([string]$text, [string]$color = "Gray") {
    Write-Host $text -ForegroundColor $color
}

# Запускает программу; то, что она пишет в stderr, не считается ошибкой PowerShell. Возвращает код выхода и весь вывод.
function Invoke-Native([string]$exe, [string[]]$arguments) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $text = ""
    $code = -1
    try {
        $text = (& $exe @arguments 2>&1 | Out-String)
        $code = $LASTEXITCODE
    } catch {
        $text = $_.Exception.Message
    } finally {
        $ErrorActionPreference = $old
    }
    return [pscustomobject]@{ Code = $code; Text = $text }
}

function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $places = @()
    if ($env:JAVA_HOME) { $places += (Join-Path $env:JAVA_HOME "bin") }
    $roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA) | Where-Object { $_ }
    foreach ($root in $roots) {
        foreach ($sub in @("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft", "Android\Android Studio\jbr", "Android\Android Studio\jre")) {
            $places += (Join-Path $root $sub)
        }
    }
    foreach ($place in $places) {
        if (-not (Test-Path -LiteralPath $place -PathType Container)) { continue }
        $direct = Join-Path $place "keytool.exe"
        if (Test-Path -LiteralPath $direct -PathType Leaf) { return $direct }
        $found = Get-ChildItem -LiteralPath $place -Filter "keytool.exe" -Recurse -Depth 4 -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

# Случайный пароль из букв и цифр без похожих символов (0/O, 1/l/I); отбрасывание лишних значений делает выбор равномерным.
function New-Password([int]$length = 24) {
    $chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    $limit = 256 - (256 % $chars.Length)
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $sb = New-Object System.Text.StringBuilder
    try {
        while ($sb.Length -lt $length) {
            $rng.GetBytes($bytes)
            foreach ($b in $bytes) {
                if ($b -lt $limit -and $sb.Length -lt $length) { [void]$sb.Append($chars[$b % $chars.Length]) }
            }
        }
    } finally {
        $rng.Dispose()
    }
    return $sb.ToString()
}

# Лежит ли папка внутри репозитория git (тогда ключ легко случайно отправить на GitHub).
function Test-InsideRepository([string]$path) {
    $dir = $path
    while (-not [string]::IsNullOrEmpty($dir)) {
        if (Test-Path -LiteralPath (Join-Path $dir ".git")) { return $true }
        $parent = Split-Path -Parent $dir
        if ([string]::IsNullOrEmpty($parent) -or $parent -eq $dir) { break }
        $dir = $parent
    }
    return $false
}

function Write-Utf8([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Copy-ToClipboard([string]$text) {
    try {
        Set-Clipboard -Value $text
        return $true
    } catch {
        return $false
    }
}

function Invoke-Main {
    $keytool = Find-Keytool
    if (-not $keytool) {
        Say "Не нашёл keytool: он входит в Java JDK, а Java на этом компьютере нет." Yellow
        Say "Поставьте её одной командой (Windows 10 и 11):" Gray
        Say "    winget install EclipseAdoptium.Temurin.17.JDK" White
        Say "затем закройте это окно, откройте НОВОЕ окно PowerShell и запустите скрипт снова." Gray
        throw "keytool не найден."
    }
    Say ("keytool:  " + $keytool) Gray

    $dir = $Out
    if ([string]::IsNullOrWhiteSpace($dir)) {
        $dir = Join-Path ([Environment]::GetFolderPath("MyDocuments")) "List-signing-key"
    }
    $dir = [System.IO.Path]::GetFullPath($dir)
    if (Test-InsideRepository $dir) {
        throw ("Папка " + $dir + " лежит внутри репозитория git. Ключ нельзя хранить там: его легко отправить на GitHub вместе с кодом. Выберите другую папку, например: -Out ""D:\List-key""")
    }

    $keyFile = Join-Path $dir "list-release.p12"
    $passFile = Join-Path $dir "list-release-password.txt"
    $b64File = Join-Path $dir "list-release-KEYSTORE_BASE64.txt"
    foreach ($f in @($keyFile, $passFile, $b64File)) {
        if (Test-Path -LiteralPath $f) {
            throw ("В папке " + $dir + " уже есть файл " + (Split-Path -Leaf $f) + ". Ключи скрипт не перезаписывает: если потерять ключ, приложения, подписанные им, больше не обновить. Если вы уверены, что старый ключ не нужен, переименуйте папку или укажите другую: -Out ""D:\List-key2""")
        }
    }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Say ("Папка:    " + $dir) Gray

    $password = New-Password 24
    Say "Создаю ключ (несколько секунд)..." Gray
    $make = Invoke-Native $keytool @(
        "-genkeypair", "-keystore", $keyFile, "-storetype", "PKCS12", "-alias", $Alias,
        "-keyalg", "RSA", "-keysize", "3072", "-validity", "36500",
        "-dname", "CN=List, O=List, C=US", "-storepass", $password, "-keypass", $password
    )
    if ($make.Code -ne 0 -or -not (Test-Path -LiteralPath $keyFile)) {
        Say "keytool не смог создать ключ. Если в пути к папке есть необычные символы, укажите простой путь, например -Out ""C:\list-key""." Yellow
        throw ("keytool вернул код " + $make.Code + ". " + $make.Text.Trim())
    }

    $check = Invoke-Native $keytool @("-list", "-v", "-keystore", $keyFile, "-storepass", $password, "-alias", $Alias)
    $fingerprint = ""
    if ($check.Code -eq 0 -and $check.Text -match "(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}") { $fingerprint = $Matches[0].ToUpper() }
    if (-not $fingerprint) {
        throw ("Ключ создан, но при проверке не открылся (код " + $check.Code + "). " + $check.Text.Trim())
    }

    $b64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($keyFile))
    Write-Utf8 $passFile ($password + "`r`n" + "Пароль ключа List: хранилище list-release.p12, псевдоним " + $Alias + ". Первая строка файла - сам пароль.`r`n")
    Write-Utf8 $b64File $b64

    $short = (($fingerprint -split ":") | Select-Object -First 8) -join ":"
    Say ""
    Say "Ключ создан." Green
    Say ("  Ключ:      " + $keyFile) White
    Say ("  Пароль:    " + $passFile) White
    Say ("  Отпечаток: " + $fingerprint) Cyan
    Say ("  В «О приложении» после перехода будет написано: Подпись " + $short + "…") Gray
    Say ""
    Say "ВАЖНО: сохраните папку с ключом и паролем в надёжное место (менеджер паролей, личное облако, флешка)." Yellow
    Say "Потеряете ключ или пароль - приложение с этой подписью больше нельзя будет обновить. Никому их не показывайте." Yellow
    Say "Если «Документы» синхронизируются с OneDrive, ключ окажется и там: как копия это нормально, но не делитесь этой папкой." Gray

    # Секреты репозитория: значения кладутся в буфер обмена по очереди.
    $url = "https://github.com/" + $Repo + "/settings/secrets/actions/new"
    Say ""
    Say "Теперь два секрета на GitHub (так сборка получит ключ)." Cyan
    Say ("  Откройте в браузере: " + $url) White
    Say "  Для каждого секрета: впишите имя, вставьте значение (Ctrl+V) и нажмите Add secret." Gray
    Say "  Вернитесь в это окно и нажмите Enter, когда страница открыта." Gray
    [void](Read-Host "Enter")

    Say ""
    Say "Секрет 1. Имя:  KEYSTORE_BASE64" White
    if (Copy-ToClipboard $b64) {
        Say "  Значение (длинный текст) уже в буфере обмена: вставьте его в поле Secret." Gray
    } else {
        Say ("  Буфер обмена недоступен: откройте файл " + $b64File + ", скопируйте весь текст и вставьте в поле Secret.") Yellow
    }
    [void](Read-Host "Когда секрет добавлен, нажмите Enter")

    Say ""
    Say "Секрет 2. Имя:  KEYSTORE_PASSWORD" White
    if (Copy-ToClipboard $password) {
        Say "  Пароль уже в буфере обмена: вставьте его в поле Secret." Gray
    } else {
        Say ("  Буфер обмена недоступен: пароль - первая строка файла " + $passFile + ".") Yellow
    }
    [void](Read-Host "Когда секрет добавлен, нажмите Enter")
    [void](Copy-ToClipboard " ")

    Say ""
    Say "Готово. Дальше (подробно в README, раздел «Свой ключ подписи»):" Green
    Say "  1. Соберите версию (тег v...): в сводке запуска Actions будет написано, что подпись «your own key», и тот же отпечаток." Gray
    Say "  2. На телефоне: «Сделать резервную копию» в List, удалить приложение, поставить новый APK, «Восстановить из копии»." Gray
    Say ("  3. Файл " + (Split-Path -Leaf $b64File) + " больше не нужен: после добавления секрета его можно удалить (ключ остаётся в list-release.p12).") Gray
}

try {
    Invoke-Main
} catch {
    Say ""
    Say ("Ошибка: " + $_.Exception.Message) Red
    Say "Ключ не создан или не готов; ничего не отправлено." Gray
    exit 1
}
