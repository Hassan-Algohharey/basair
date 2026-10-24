param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v010-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v010-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v010-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','input','swipe',([int]($width*.5)).ToString(),([int]($height*$from)).ToString(),([int]($width*.5)).ToString(),([int]($height*$to)).ToString(),'250') | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 20;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v010.png') | Out-Null
    Device @('pull','/sdcard/basair-v010.png',(Join-Path $shots $Name)) | Out-Null
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
$shortCount=@($catalog.items | Where-Object { $_.type -eq 'short' }).Count
$sermonCount=@($catalog.items | Where-Object { $_.type -eq 'sermon' }).Count
$lessonCount=@($catalog.items | Where-Object { $_.type -eq 'lesson' }).Count
$nawawiCount=@($catalog.items | Where-Object { 'nawawi-fifty' -in $_.bookIds }).Count
$totalCount=@($catalog.items).Count
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'الشيوخ والدروس')
ToolbarTap 'مقاطع قصيرة'
Find "عدد المواد: $shortCount" | Out-Null
Capture '01-shorts.png'
Top
ToolbarTap 'الخطب'
Find "عدد المواد: $sermonCount" | Out-Null
Top
ToolbarTap 'الدروس وشروح الكتب'
Find "عدد المواد: $lessonCount" | Out-Null
Top
Search 'tahawiyyah'
TapNode (Show 'المصدر والمشاهدة')
Find 'فتح خارج التطبيق' | Out-Null
if (-not (Contains 'binbaz.org.sa/audios/')) { throw 'Reviewed full source URL missing' }
Capture '02-source.png'
Tap 'إغلاق'
$createdFavorite=$false
if (Lookup 'حفظ في المفضلة') { Tap 'حفظ في المفضلة'; $createdFavorite=$true }
TapNode (Show 'قراءة الكتاب: العقيدة الطحاوية')
WaitReader 'صفحة الملف ' | Out-Null
Capture '03-linked-reader.png'
Device @('shell','input','keyevent','4') | Out-Null
Show 'إزالة من المفضلة' | Out-Null
Top
Find 'tahawiyyah' | Out-Null
Restart
Tap 'محفوظاتي'
TapNode (Show 'مفضلة الدروس والخطب')
TapNode (Show '01 مقدمة المؤلف')
Show 'إزالة من المفضلة' | Out-Null
Capture '04-favorite-restart.png'
if ($createdFavorite) { Tap 'إزالة من المفضلة' }
Restart
Tap 'البحث'
Search 'tahawiyyah'
TapNode (Show 'الشيوخ والدروس')
TapNode (Show 'عدد المواد: 3')
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'الشيوخ والدروس')
TapNode (Show 'عرض الصفحة والدروس')
Show 'المواد المفهرسة: 6' | Out-Null
Show 'عدد المواد: 6' | Out-Null
Capture '06-scholar-profile.png'
Top
TapNode (Show 'فتح مصدر التعريف خارج التطبيق')
Start-Sleep -Seconds 2
$activity=Device @('shell','dumpsys','activity','activities')
if (($activity -join ' ') -notmatch 'dat=https://binbaz.org.sa/about') { throw 'Official profile URL was not sent to browser' }
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Top
TapNode (Show 'العودة إلى دليل الشيوخ')
Restart
Tap 'المكتبة'
Tap 'الحديث'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
WaitReader 'صفحة الملف ' | Out-Null
ToolbarTap 'شروح الكتاب'
Show "عدد المواد: $nawawiCount" | Out-Null
Capture '07-book-explanations.png'
TapNode (Show 'قراءة الكتاب: الأربعون النووية مع زيادة ابن رجب')
WaitReader 'صفحة الملف ' | Out-Null
Device @('shell','input','keyevent','4') | Out-Null
Top
TapNode (Show 'شروح الأربعون النووية مع زيادة ابن رجب — عرض كل الكتب')
Show "عدد المواد: $totalCount" | Out-Null
Restart
Tap 'English'
TapNode (Show 'Scholars and lessons')
ToolbarTap 'Short talks'
Find "Items found: $shortCount" | Out-Null
Capture '05-english.png'
Restart
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Write-Output 'PASS: phone current-catalog media filters, source URL, linked reader/back state, favorite restart, global search, profiles, external browser, book explanations and Arabic/English.'
