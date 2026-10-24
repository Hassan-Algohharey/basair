param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", [string]$Serial = 'emulator-5580', [switch]$PhonePreview)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$' -and -not $PhonePreview) { throw 'Physical phone checks require -PhonePreview and only use the separate preview package.' }
$package = if ($PhonePreview) { 'com.algohary.basair.preview' } else { 'com.algohary.basair' }
$project = Split-Path $PSScriptRoot -Parent
$shots = Join-Path $project $(if ($PhonePreview) { 'publishing/screenshots/v05-phone' } else { 'docs/screenshots/v05' })
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Read-Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-reader-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-reader-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
    [xml](Get-Content (Join-Path $shots 'ui.xml') -Raw -Encoding UTF8)
}
function Find([string]$Text) {
    for ($attempt=0; $attempt -lt 4; $attempt++) {
        $node = (Read-Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and ($_.text -eq $Text -or $_.'content-desc' -eq $Text) } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "Element missing: $Text"
}
function TapNode($Node) {
    while ($Node.clickable -ne 'true' -and $Node.ParentNode.Name -eq 'node') { $Node = $Node.ParentNode }
    $b = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Device @('shell','input','tap',([int](($b[0]+$b[2])/2)).ToString(),([int](($b[1]+$b[3])/2)).ToString()) | Out-Null
    Start-Sleep -Milliseconds 250
}
function Tap([string]$Text) { TapNode (Find $Text) }
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-reader.png') | Out-Null
    Device @('pull','/sdcard/basair-reader.png',(Join-Path $shots $Name)) | Out-Null
}
function AssertAbsent([string]$Text) {
    if ((Read-Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -eq $Text }) { throw "Unexpected item: $Text" }
}
function Bookmarks([int]$Count) {
    $label = "علامات الصفحات ($Count)"
    $ui = Read-Ui
    $node = $ui.SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -eq $label } | Select-Object -First 1
    if (-not $node) {
        $row = $ui.SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.scrollable -eq 'true' -and $_.bounds -match '^\[0,' } | Select-Object -First 1
        if ($row) {
            $b = [regex]::Matches($row.bounds, '\d+') | ForEach-Object { [int]$_.Value }
            $y = ([int](($b[1]+$b[3])/2)).ToString()
            Device @('shell','input','swipe','100',$y,'900',$y,'300') | Out-Null
        }
    }
    Tap $label
}
Device @('shell','am','force-stop',$package) | Out-Null
if (-not $PhonePreview) { Device @('shell','pm','clear',$package) | Out-Null }
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المكتبة'
Tap 'الفقه الميسر في ضوء الكتاب والسنة'
Find 'صفحة الملف 1 من 469 · الفقه الميسر في ضوء الكتاب والسنة' | Out-Null
Capture '01-reader-day.png'
Tap 'وضع علامة للصفحة'
Tap 'التالية'
Find 'صفحة الملف 2 / 469' | Out-Null
Tap 'وضع علامة للصفحة'
Bookmarks 2
Find 'انتقال إلى صفحة 1' | Out-Null
Find 'انتقال إلى صفحة 2' | Out-Null
Capture '02-bookmarks.png'
Tap 'انتقال إلى صفحة 1'
Find 'صفحة الملف 1 / 469' | Out-Null
Tap 'الوضع الليلي'
Tap '+'
Find '150٪' | Out-Null
Capture '03-reader-night.png'
Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المكتبة'
Tap 'الفقه الميسر في ضوء الكتاب والسنة'
Find 'الوضع النهاري' | Out-Null
Find '150٪' | Out-Null
Find 'صفحة الملف 1 / 469' | Out-Null
Bookmarks 2
Tap 'حذف علامة 1'
AssertAbsent 'انتقال إلى صفحة 1'
Find 'انتقال إلى صفحة 2' | Out-Null
Tap 'إغلاق'
Tap '→ العودة'
Tap 'العقيدة الطحاوية'
Bookmarks 0
Find 'لا توجد علامات بعد. استخدم «وضع علامة للصفحة» أثناء القراءة.' | Out-Null
Capture '04-empty-other-book.png'
Tap 'إغلاق'
Tap 'الوضع النهاري'
Tap '→ العودة'
Tap 'الفقه الميسر في ضوء الكتاب والسنة'
Find 'الوضع الليلي' | Out-Null
Bookmarks 1
Tap 'انتقال إلى صفحة 2'
Find 'صفحة الملف 2 / 469' | Out-Null
Capture '05-restored-page.png'
Write-Output 'PASS v0.5: two bookmarks, jump, deletion, per-book isolation, night and zoom persistence, return to library, original page rendering.'
