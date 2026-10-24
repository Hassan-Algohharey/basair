param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v012-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v012-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v012-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    $from=if ($Up) { .65 } else { .30 }; $to=if ($Up) { .43 } else { .76 }
    $x=[int]($width*.5); $start=[int]($height*$from); $end=[int]($height*$to)
    if (Test-Path (Join-Path $shots 'ui.xml')) {
        [xml]$lastUi=Get-Content (Join-Path $shots 'ui.xml') -Raw -Encoding UTF8
        $dialogScroll=$lastUi.SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.ScrollView' -and $_.scrollable -eq 'true' } | Select-Object -First 1
        if ($dialogScroll) {
            $bounds=[regex]::Matches($dialogScroll.bounds,'\d+') | ForEach-Object { [int]$_.Value }
            $x=[int](($bounds[0]+$bounds[2])/2); $span=$bounds[3]-$bounds[1]
            $start=[int]($bounds[1]+$span*$(if ($Up) { .8 } else { .25 }))
            $end=[int]($bounds[1]+$span*$(if ($Up) { .3 } else { .75 }))
        }
    }
    Device @('shell','input','swipe',$x.ToString(),$start.ToString(),$x.ToString(),$end.ToString(),'350') | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 20;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v012.png') | Out-Null
    Device @('pull','/sdcard/basair-v012.png',(Join-Path $shots $Name)) | Out-Null
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
    $labelNode=Show $Label
    $b=[regex]::Matches($labelNode.bounds,'\d+') | ForEach-Object { [int]$_.Value }
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


function AudioPosition {
    $line=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.StartsWith('موضع الاستماع:') } | Select-Object -First 1
    if (-not $line) { $line=Show 'تشغيل'; throw 'Listening position not visible' }
    $clock=[regex]::Match($line.text,'(\d+):(\d+):(\d+)')
    return ([int64]$clock.Groups[1].Value*3600 + [int64]$clock.Groups[2].Value*60 + [int64]$clock.Groups[3].Value)*1000
}
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'الكتب المسموعة')
TapNode (Show 'العقيدة الطحاوية — قراءة ياسر سلامة')
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
$before=AudioPosition
Capture '01-streaming-player.png'
Device @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Seconds 6
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Show 'إيقاف مؤقت' | Out-Null
$after=AudioPosition
if ($after -lt ($before+4000)) { throw "Background playback did not advance: $before -> $after" }
Capture '02-return-from-background.png'
Tap 'إيقاف مؤقت'
$paused=AudioPosition
Tap 'تقديم 15ث'
$forward=AudioPosition
if ($forward -lt $paused+13000) { throw "Forward seek failed: $paused -> $forward" }
Tap 'رجوع 15ث'
$back=AudioPosition
if ([math]::Abs($back-$paused) -gt 3000) { throw "Back seek failed: $paused -> $back" }
TapNode (Show '1.5×')
Start-Sleep -Seconds 1
Restart
TapNode (Show 'الكتب المسموعة')
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
Tap 'إيقاف مؤقت'
$resumed=AudioPosition
if ($resumed -lt $paused-3000 -or $resumed -gt $paused+15000) { throw "Resume position failed: $paused -> $resumed" }
Capture '03-position-after-force-stop.png'
TapNode (Show 'إيقاف المشغل')
Write-Output "PASS: audio playback, foreground/background continuation $before -> $after, +/-15seek, speed and persisted position $paused -> $resumed."

Restart
TapNode (Show 'الكتب المسموعة')
TapNode (Show 'العقيدة الطحاوية — قراءة ياسر سلامة')
$downloadButton=Show 'تنزيل للاستماع دون اتصال'
$button=$downloadButton
while ($button.clickable -ne 'true' -and $button.ParentNode.Name -eq 'node') { $button=$button.ParentNode }
if ($button.enabled -eq 'true') { TapNode $downloadButton }
$completed=$false
for ($i=0;$i -lt 30;$i++) { if (Lookup 'نسخة محفوظة متاحة دون اتصال') { $completed=$true;break }; Start-Sleep -Seconds 1 }
if (-not $completed) { throw 'Audio download did not finalize' }
Capture '04-downloaded.png'
Top
$wifiState=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
$dataState=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
try {
    Device @('shell','svc','wifi','disable') | Out-Null
    Device @('shell','svc','data','disable') | Out-Null
    TapNode (Show 'تشغيل')
    Show 'إيقاف مؤقت' | Out-Null
    $offlineBefore=AudioPosition
    Start-Sleep -Seconds 3
    $offlineAfter=AudioPosition
    if ($offlineAfter -le $offlineBefore) { throw 'Saved audio did not play with Wi-Fi and mobile data disabled' }
    Capture '05-offline-playback.png'
    Tap 'إيقاف مؤقت'
    TapNode (Show 'إيقاف المشغل')
} finally {
    if ($wifiState -eq '1') { Device @('shell','svc','wifi','enable') | Out-Null }
    if ($dataState -eq '1') { Device @('shell','svc','data','enable') | Out-Null }
}
Restart
Tap 'البحث'
Search 'alansari'
TapNode (Show 'الكتب المسموعة')
Show 'القراءات المتاحة: 1' | Out-Null
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
Tap 'إيقاف مؤقت'
Capture '06-second-reading.png'
TapNode (Show 'قراءة الكتاب: الأربعون النووية مع زيادة ابن رجب')
WaitReader 'صفحة الملف ' | Out-Null
Capture '07-linked-book.png'
ToolbarTap 'استمع إلى الكتاب'
Show 'القراءات المتاحة: 1' | Out-Null
TapNode (Show 'إيقاف المشغل')
Restart
Tap 'English'
TapNode (Show 'Audiobooks')
Show 'Playback speed' | Out-Null
Show 'Source and rights: IslamHouse' | Out-Null
Capture '08-english-player.png'
Restart
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Write-Output "PASS: downloads, offline playback, both readings, global search, linked reader and Arabic/English."
