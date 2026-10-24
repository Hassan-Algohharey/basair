param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v011-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v011-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v011-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v011.png') | Out-Null
    Device @('pull','/sdcard/basair-v011.png',(Join-Path $shots $Name)) | Out-Null
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

$catalog=Get-Content (Join-Path $project 'content/media/catalog.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$tahawi=$catalog.series | Where-Object { $_.id -eq 'bin-baz-tahawi' }
$wasael=$catalog.series | Where-Object { $_.id -eq 'barrak-wasael' }
$third=$catalog.items | Where-Object { $_.id -eq 'bin-baz-tahawi-03' }
$first=$catalog.items | Where-Object { $_.id -eq 'bin-baz-tahawi-01' }
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'الشيوخ والدروس')
$hadLast=[bool](Lookup 'آخر مادة فتحت مصدرها')
TapNode (Show 'اختيار سلسلة')
Tap $tahawi.title
Show 'مجموعة جزئية من حلقات السلسلة' | Out-Null
Show 'الحلقات المفهرسة: 3' | Out-Null
Show 'عدد المواد: 3' | Out-Null
Capture '01-partial-series.png'
TapNode (Show 'المصدر والمشاهدة')
Find $first.title | Out-Null
Tap 'المادة التالية'
Find $third.title | Out-Null
Capture '02-indexed-next-skips-missing2.png'
Tap 'المادة السابقة'
Find $first.title | Out-Null
Tap 'المادة التالية'
if (-not (Contains $third.url)) { throw 'Selected episode source URL missing' }
Tap 'فتح خارج التطبيق'
Start-Sleep -Seconds 2
$activity=Device @('shell','dumpsys','activity','activities')
if (($activity -join ' ') -notmatch 'topResumedActivity=.*com.android.chrome') { throw 'External browser was not opened' }
Restart
TapNode (Show 'الشيوخ والدروس')
Show 'آخر مادة فتحت مصدرها' | Out-Null
TapNode (Show 'العودة إلى المادة')
Find $third.title | Out-Null
Capture '03-last-opened-after-restart.png'
Tap 'إغلاق'
Top
TapNode (Show 'اختيار سلسلة')
Tap $wasael.title
Show 'السلسلة مفهرسة كاملة بحسب المصدر' | Out-Null
Show 'الحلقات المفهرسة: 7' | Out-Null
Show 'عدد المواد: 7' | Out-Null
Capture '04-complete-source-series.png'
TapNode (Show 'قراءة الكتاب: الوسائل المفيدة للحياة السعيدة')
WaitReader 'صفحة الملف ' | Out-Null
Capture '05-new-linked-book.png'
Device @('shell','input','keyevent','4') | Out-Null
Top
Show 'السلسلة مفهرسة كاملة بحسب المصدر' | Out-Null
TapNode (Show 'إلغاء تصفية السلسلة')
Top
ToolbarTap 'الخطب'
Top
TapNode (Show 'اختيار موضوع')
TapNode (Show 'الدعاء')
Show 'عدد المواد: 2' | Out-Null
Capture '06-sermons-supplication.png'
Top
Search 'supplication'
Show 'عدد المواد: 2' | Out-Null
Top
TapNode (Show 'إعادة الضبط')
Show 'عدد المواد: 33' | Out-Null
Restart
Tap 'English'
TapNode (Show 'Scholars and lessons')
TapNode (Show 'Choose series')
Tap $wasael.englishTitle
Show 'Fully indexed series according to the source' | Out-Null
Show 'Indexed episodes: 7' | Out-Null
Capture '07-english-complete-series.png'
Top
TapNode (Show 'Clear series filter')
Top
ToolbarTap 'Sermons'
Top
TapNode (Show 'Choose topic')
TapNode (Show 'Supplication')
Show 'Items found: 2' | Out-Null
Capture '08-english-topic.png'
Restart
Tap 'العربية'
TapNode (Show 'الشيوخ والدروس')
if (-not $hadLast) { TapNode (Show 'مسح آخر مادة') }
Restart
Find 'الرئيسية' | Out-Null

Restart
TapNode (Show 'الشيوخ والدروس')
if (-not $hadLast -and (Lookup 'آخر مادة فتحت مصدرها')) { throw 'Cleared last-material record came back after restart' }
TapNode (Show 'اختيار سلسلة')
Tap 'شرح الأربعين النووية'
Show 'مجموعة جزئية من حلقات السلسلة' | Out-Null
Show 'الحلقات المفهرسة: 8' | Out-Null
Show 'عدد المواد: 8' | Out-Null
Capture '09-nawawi-eight.png'
Restart
Tap 'البحث'
Search 'beneficial'
TapNode (Show 'الشيوخ والدروس')
Show 'عدد المواد: 7' | Out-Null
Capture '10-global-series-search.png'
Restart
TapNode (Show 'القرآن الكريم والتفسير الميسر')
Tap 'القراءة'
Find 'تفسير الآية' | Out-Null
Restart
Find 'الرئيسية' | Out-Null
Write-Output 'PASS: phone partial/complete series, original numbering and next/previous, last-opened restart, new linked book/back state, compound sermon/topic/query filters and Arabic/English.'
