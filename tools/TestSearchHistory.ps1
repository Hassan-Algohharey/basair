param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project ('publishing/screenshots/v016-' + $(if ($Serial.StartsWith('emulator-')) { 'emulator' } else { 'phone' }))
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v016-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v016-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v016.png') | Out-Null
    Device @('pull','/sdcard/basair-v016.png',(Join-Path $shots $Name)) | Out-Null
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

function AudioPosition {
    $line=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.StartsWith('موضع الاستماع:') } | Select-Object -First 1
    if (-not $line) { throw 'Listening position not visible' }
    $clock=[regex]::Match($line.text,'(\d+):(\d+):(\d+)')
    return ([int64]$clock.Groups[1].Value*3600+[int64]$clock.Groups[2].Value*60+[int64]$clock.Groups[3].Value)*1000
}
function WaitVerse([int]$Number) {
    for ($i=0;$i -lt 18;$i++) {
        $node=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -match "الآية $Number`$" } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "Verse $Number did not open"
}
function Enter([string]$Value) {
    Top
    $field=$null
    for ($i=0;$i -lt 12;$i++) {
        $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
        if ($field) { break }; Swipe $true
    }
    if (-not $field) { throw 'Search field missing' }
    if ($field.text) { TapNode (Find 'مسح'); $field=(Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' } | Select-Object -First 1 }
    TapNode $field
    Device @('shell','input','text',$Value) | Out-Null
    Device @('shell','input','keyevent','4') | Out-Null
}
function OpenHistory { TapNode (Show 'سجل البحث') }
Restart
if (Lookup 'Home') { Tap 'العربية' }
Tap 'البحث'
Enter 'never-saved'
OpenHistory
Show 'عمليات البحث المحفوظة: 0' | Out-Null
Show 'حفظ البحث مفعل' | Out-Null
Tap '→ العودة'
Enter 'hadith'
TapNode (Show 'بحث')
OpenHistory
Show 'عمليات البحث المحفوظة: 1' | Out-Null
Show 'الكتب والبحث العام' | Out-Null
TapNode (Show 'hadith')
OpenHistory
Show 'عمليات البحث المحفوظة: 1' | Out-Null
Capture '01-book-history.png'
Restart
TapNode (Show 'الشيوخ والدروس')
Enter 'Abdul%sQadir'
TapNode (Show 'بحث')
OpenHistory
Show 'عمليات البحث المحفوظة: 2' | Out-Null
TapNode (Show 'Abdul Qadir')
Show 'عدد المواد: 19' | Out-Null
Capture '02-replayed-scholar.png'
Enter 'tuhfat'
TapNode (Show 'المصدر والمشاهدة')
TapNode (Show 'إغلاق')
Top
OpenHistory
Show 'عمليات البحث المحفوظة: 3' | Out-Null
Capture '03-books-scholars-lessons.png'
Restart
OpenHistory
Show 'عمليات البحث المحفوظة: 3' | Out-Null
TapNode (Show 'حذف البحث: hadith')
Show 'عمليات البحث المحفوظة: 2' | Out-Null
Tap 'حفظ سجل البحث على هذا الجهاز'
Show 'حفظ البحث متوقف' | Out-Null
TapNode (Show 'tuhfat')
Enter 'private-disabled'
TapNode (Show 'بحث')
OpenHistory
Show 'عمليات البحث المحفوظة: 2' | Out-Null
if (Lookup 'private-disabled') { throw 'Disabled query was saved' }
Restart
OpenHistory
Show 'حفظ البحث متوقف' | Out-Null
Show 'عمليات البحث المحفوظة: 2' | Out-Null
Capture '04-disabled-persists.png'
TapNode (Show 'حذف سجل البحث كله')
Show 'لا توجد عمليات بحث محفوظة' | Out-Null
Restart
OpenHistory
Show 'عمليات البحث المحفوظة: 0' | Out-Null
Show 'حفظ البحث متوقف' | Out-Null
Tap 'حفظ سجل البحث على هذا الجهاز'
Show 'حفظ البحث مفعل' | Out-Null
$wifiState=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
$dataState=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
try {
    Device @('shell','svc','wifi','disable') | Out-Null
    Device @('shell','svc','data','disable') | Out-Null
    Tap 'البحث'
    Enter 'offline-local'
    TapNode (Show 'بحث')
    OpenHistory
    Show 'عمليات البحث المحفوظة: 1' | Out-Null
    Restart
    OpenHistory
    Show 'offline-local' | Out-Null
    Capture '05-offline-persistence.png'
    TapNode (Show 'حذف سجل البحث كله')
    Show 'لا توجد عمليات بحث محفوظة' | Out-Null
} finally {
    if ($wifiState -eq '1') { Device @('shell','svc','wifi','enable') | Out-Null }
    if ($dataState -eq '1') { Device @('shell','svc','data','enable') | Out-Null }
}
Restart
Tap 'English'
TapNode (Show 'Search history')
Show 'Saved searches: 0' | Out-Null
Tap 'Save search history on this device'
Show 'Search saving is off' | Out-Null
Restart
TapNode (Show 'Search history')
Show 'Search saving is off' | Out-Null
Tap 'Save search history on this device'
Show 'Search saving is on' | Out-Null
Capture '06-english-settings.png'
Restart
Tap 'العربية'
OpenHistory
Show 'عمليات البحث المحفوظة: 0' | Out-Null
Show 'حفظ البحث مفعل' | Out-Null
Write-Output 'PASS: no keystroke capture; books/teacher/lesson history; section replay; deduplication; restart; individual/all deletion; persistent disabled state; no resurrection; offline; Arabic/English.'
