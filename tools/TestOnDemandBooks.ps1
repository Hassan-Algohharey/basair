param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [switch]$CaptureBeforeUpgrade
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v082-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v082-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v082-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
    [xml](Get-Content (Join-Path $shots 'ui.xml') -Raw -Encoding UTF8)
}
function Lookup([string]$Text) {
    (Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and ($_.text -eq $Text -or $_.'content-desc' -eq $Text) } | Select-Object -First 1
}
function Find([string]$Text) {
    for ($i=0;$i -lt 3;$i++) { $node=Lookup $Text; if ($node) { return $node } }
    throw "Missing: $Text"
}
function TapNode($Node) {
    while ($Node.clickable -ne 'true' -and $Node.ParentNode.Name -eq 'node') { $Node=$Node.ParentNode }
    $b=[regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Device @('shell','input','tap',([int](($b[0]+$b[2])/2)).ToString(),([int](($b[1]+$b[3])/2)).ToString()) | Out-Null
}
function Tap([string]$Text) { TapNode (Find $Text) }
$size=Device @('shell','wm','size')
$match=[regex]::Match(($size -join ' '),'(\d+)x(\d+)')
$width=[int]$match.Groups[1].Value; $height=[int]$match.Groups[2].Value
function Swipe([bool]$Up) {
    $from=if ($Up) { .76 } else { .30 }; $to=if ($Up) { .30 } else { .76 }
    Device @('shell','input','swipe',([int]($width*.5)).ToString(),([int]($height*$from)).ToString(),([int]($width*.5)).ToString(),([int]($height*$to)).ToString(),'250') | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 9;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v082.png') | Out-Null
    Device @('pull','/sdcard/basair-v082.png',(Join-Path $shots $Name)) | Out-Null
}
function Restart {
    Device @('shell','am','force-stop',$package) | Out-Null
    Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
}
function Search([string]$Query) {
    Top
    $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
    if (-not $field) { throw 'No search field' }
    TapNode $field
    Device @('shell','input','text',$Query) | Out-Null
    Device @('shell','input','keyevent','4') | Out-Null
}
function WaitReader([string]$Prefix) {
    for ($i=0;$i -lt 10;$i++) {
        $node=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.'content-desc'.StartsWith($Prefix) } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw 'PDF did not render'
}

function ToolbarTap([string]$Text) {
    foreach ($direction in @($true,$true,$false,$false,$false)) {
        $node=Lookup $Text
        if ($node) { TapNode $node; return }
        $row=(Ui).SelectNodes('//node') | Where-Object {
            if ($_.package -ne $package -or $_.scrollable -ne 'true') { return $false }
            $b=[regex]::Matches($_.bounds,'\d+') | ForEach-Object { [int]$_.Value }
            ($b[3]-$b[1]) -lt 300
        } | Select-Object -First 1
        if (-not $row) { throw 'Reader toolbar missing' }
        $b=[regex]::Matches($row.bounds,'\d+') | ForEach-Object { [int]$_.Value }
        $y=([int](($b[1]+$b[3])/2)).ToString()
        $from=if ($direction) { '100' } else { '900' }; $to=if ($direction) { '900' } else { '100' }
        Device @('shell','input','swipe',$from,$y,$to,$y,'250') | Out-Null
    }
    Tap $Text
}
function Contains([string]$Text) {
    (Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.Contains($Text) } | Select-Object -First 1
}
function Toggle([string]$Label,[bool]$Value) {
    $label=Show $Label
    $b=[regex]::Matches($label.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $center=($b[1]+$b[3])/2
    $switch=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.checkable -eq 'true' } | Sort-Object {
        $bounds=[regex]::Matches($_.bounds,'\d+') | ForEach-Object { [int]$_.Value }
        [math]::Abs(($bounds[1]+$bounds[3])/2-$center)
    } | Select-Object -First 1
    if (-not $switch) { throw "Switch missing: $Label" }
    if (($switch.checked -eq 'true') -ne $Value) { TapNode $switch }
}
function Jump([int]$Target) {
    $button=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -match '^صفحة الملف \d+ / \d+$' } | Select-Object -First 1
    TapNode $button
    $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
    TapNode $field
    Device @('shell','input','keyevent','123') | Out-Null
    1..5 | ForEach-Object { Device @('shell','input','keyevent','67') | Out-Null }
    Device @('shell','input','text',$Target.ToString()) | Out-Null
    Tap 'انتقال'
}
if ($CaptureBeforeUpgrade) {
    Restart
    if (Lookup 'Home') { Tap 'العربية' }
    Tap 'المكتبة'
    Tap 'الفقه'
    Tap 'الشافعي (1)'
    TapNode (Show 'فتح القريب المجيب بشرح متن أبي شجاع')
    $state=WaitReader 'صفحة الملف '
    $state.'content-desc' | Set-Content (Join-Path $shots 'before-upgrade.txt') -Encoding UTF8
    Tap '→ العودة'
    Restart
    Tap 'المكتبة'
    Tap 'الحديث'
    TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
    WaitReader 'صفحة الملف ' | Out-Null
    Tap '→ العودة'
    Restart
    Write-Output ('Pre-upgrade reading state: '+$state.'content-desc')
    exit
}
$offlineLabel='قراءة المحفوظ فقط؛ إيقاف اتصالات تنزيل الكتب والصفحات.'
$wifiLabel='جلب الصفحات المجاورة على اتصال غير محدود فقط؛ الصفحة المطلوبة تنزل عند فتحها.'
Restart
if (Lookup 'Home') { Tap 'العربية' }
Tap 'المكتبة'
Tap 'الفقه'
Tap 'الشافعي (1)'
TapNode (Show 'فتح القريب المجيب بشرح متن أبي شجاع')
$previous=(Get-Content (Join-Path $shots 'before-upgrade.txt') -Raw -Encoding UTF8).Trim()
$now=WaitReader 'صفحة الملف '
if ($now.'content-desc' -ne $previous) { throw 'Pre-upgrade reading position changed' }
ToolbarTap 'التنزيل والتخزين'
Find 'الكتاب كامل محفوظ ومطابق للأصل، وجاهز دون إنترنت.' | Out-Null
Capture '01-migrated-original.png'
Tap 'إغلاق'
Tap '→ العودة'
Restart
Tap 'المكتبة'
Tap 'الحديث'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
$before=WaitReader 'صفحة الملف '
$oldPage=[int]([regex]::Match($before.'content-desc','صفحة الملف (\d+)').Groups[1].Value)
Jump 30
WaitReader 'صفحة الملف 30 من 61' | Out-Null
ToolbarTap 'التنزيل والتخزين'
Toggle $wifiLabel $false
Top
TapNode (Show 'حذف ملفات هذا الكتاب')
Top
if (-not (Contains 'المحفوظ: 0 من 61')) { throw 'Book files were not removed' }
Tap 'إغلاق'
WaitReader 'صفحة الملف 30 من 61' | Out-Null
$prefetched=$false
for ($i=0;$i -lt 10;$i++) {
    Start-Sleep -Seconds 10
    ToolbarTap 'التنزيل والتخزين'
    if (Contains 'صفحات الملف المحفوظة: 23–37') { $prefetched=$true; break }
    Tap 'إغلاق'
}
if (-not $prefetched) { throw 'Exact page window 23-37 was not cached' }
if (-not (Contains 'المحفوظ: 15 من 61')) { throw 'Expected exactly fifteen cached pages' }
Capture '02-page-window.png'
Toggle $offlineLabel $true
Tap 'إغلاق'
Jump 23
WaitReader 'صفحة الملف 23 من 61' | Out-Null
Capture '03-cached-only-page.png'
Jump 1
Find 'هذه الصفحة غير محفوظة. أوقف «قراءة المحفوظ فقط» من التنزيل والتخزين لتحميلها عند توفر الإنترنت.' | Out-Null
Capture '04-missing-local-page.png'
Jump 30
WaitReader 'صفحة الملف 30 من 61' | Out-Null
Restart
Tap 'المكتبة'
Tap 'الحديث'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
WaitReader 'صفحة الملف 30 من 61' | Out-Null
Write-Output 'PASS upgrade migration, unchanged reading position, exact 15-page window 23-37, saved-only reading, uncached-page message, process restart without data clearing.'
ToolbarTap 'التنزيل والتخزين'
Toggle $offlineLabel $false
Top
TapNode (Show 'تنزيل الكتاب كاملًا')
$complete=$false
for ($i=0;$i -lt 20;$i++) {
    if (Lookup 'الكتاب كامل محفوظ ومطابق للأصل، وجاهز دون إنترنت.') { $complete=$true; break }
    Start-Sleep -Seconds 2
}
if (-not $complete) { throw 'Complete original did not download and verify' }
Capture '05-complete-download.png'
Toggle $wifiLabel $true
Top
Toggle $offlineLabel $true
Tap 'إغلاق'
Jump 61
WaitReader 'صفحة الملف 61 من 61' | Out-Null
Capture '06-complete-offline-last.png'
ToolbarTap 'التنزيل والتخزين'
Toggle $offlineLabel $false
Tap 'إغلاق'
Jump $oldPage
WaitReader 'صفحة الملف ' | Out-Null
Tap '→ العودة'
Restart
Tap 'English'
Tap 'Library'
Tap 'Hadith'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
WaitReader 'File page ' | Out-Null
ToolbarTap 'Downloads and storage'
Find 'The complete book matches the original and is ready offline.' | Out-Null
Capture '07-storage-en.png'
Tap 'Close'
Tap 'Back'
Restart
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Write-Output 'PASS complete original download with checksum, complete offline last-page rendering, English storage controls, original reading position and default Arabic/network preferences restored.'