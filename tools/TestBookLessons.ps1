param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [ValidateSet('All','FinalSmoke')][string]$Phase='All'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v018-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v018-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v018-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v018.png') | Out-Null
    Device @('pull','/sdcard/basair-v018.png',(Join-Path $shots $Name)) | Out-Null
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

$links=(Get-Content (Join-Path $project 'content/media/book-lessons.json') -Raw -Encoding UTF8 | ConvertFrom-Json).links
function OpenSeries([string]$Title) {
 Restart
 if (Lookup 'Home') { Tap 'العربية' }
 TapNode (Show 'الشيوخ والدروس')
 TapNode (Show 'اختيار سلسلة'); Tap $Title
}
function AssertNoContinuation {
 Show 'إلغاء تصفية السلسلة' | Out-Null
 if (Lookup 'استكمال هذه السلسلة') { throw 'Existing series progress; preserve it and use a profile without a saved continuation for this test.' }
 Top
}

if ($Phase -eq 'FinalSmoke') {
 OpenSeries 'شرح الوسائل المفيدة للحياة السعيدة'
 TapNode (Show 'قراءة الكتاب: الوسائل المفيدة للحياة السعيدة')
 WaitReader 'صفحة الملف ' | Out-Null
 Jump 3; WaitReader 'صفحة الملف 3 من 23' | Out-Null
 ToolbarTap 'شروح هذا الموضع'; Tap 'مقدمة المؤلف: راحة القلب'
 Show 'موضع المتن المطابق' | Out-Null
 if (-not (Contains 'روجع موضع عبارة عنوانه في نسخة المكتبة')) { throw 'Final source note missing' }
 Capture 'final-source-note-ar.png'
 Tap 'إغلاق'
 Restart; Tap 'English'; TapNode (Show 'Scholars and lessons')
 TapNode (Show 'Choose series'); Tap 'Beneficial means to a happy life commentary'
 Show 'Episodes marked completed: 0 / 7 indexed' | Out-Null
 Capture 'final-series-en.png'
 Restart; Tap 'العربية'
 Write-Output 'PASS final APK source-note clarification, mapped-page route, English series controls and test-progress cleanup.'
 exit
}

OpenSeries 'شرح الوسائل المفيدة للحياة السعيدة'
AssertNoContinuation
TapNode (Show 'قراءة الكتاب: الوسائل المفيدة للحياة السعيدة')
WaitReader 'صفحة الملف ' | Out-Null
ToolbarTap 'شروح الكتاب'
TapNode (Show 'اختيار باب أو موضع مشروح')
foreach ($link in @($links | Where-Object bookId -eq 'wasael')) {
 TapNode (Show $link.title)
 TapNode (Show "قراءة الموضع · ص $($link.printedPage) · صفحة الملف $($link.pdfPage)")
 WaitReader "صفحة الملف $($link.pdfPage) من 23" | Out-Null
 Capture "passage-$($link.id).png"
 ToolbarTap 'شروح هذا الموضع'
 Tap $link.title
 $catalogItem = (Get-Content (Join-Path $project 'content/media/catalog.json') -Raw -Encoding UTF8 | ConvertFrom-Json).items | Where-Object id -eq $link.itemId
 Find $catalogItem.title | Out-Null
 Capture "lesson-$($link.id).png"
 Tap 'إغلاق'
 TapNode (Show 'اختيار باب أو موضع مشروح')
 Write-Output "PASS matched passage $($link.id): printed $($link.printedPage), file $($link.pdfPage), reader-to-lesson round trip."
}
Tap 'إغلاق'

OpenSeries 'شرح الوسائل المفيدة للحياة السعيدة'
TapNode (Show 'المصدر والمشاهدة')
$alreadyComplete=([bool](Lookup 'إلغاء اكتمال الحلقة'))
TapNode (Show 'المتابعة من هذه الحلقة')
Show 'حُفظت حلقة المتابعة لهذه السلسلة' | Out-Null
if (-not $alreadyComplete) { TapNode (Show 'تعليم الحلقة كمكتملة') }
Tap 'إغلاق'

OpenSeries 'شرح العقيدة الطحاوية'
AssertNoContinuation
TapNode (Show 'المصدر والمشاهدة')
TapNode (Show 'المتابعة من هذه الحلقة'); Tap 'إغلاق'
OpenSeries 'شرح الوسائل المفيدة للحياة السعيدة'
TapNode (Show 'استكمال هذه السلسلة')
Find '(1) المقدمة "قوله انما الاعمال بالنيات"' | Out-Null
Show 'حُفظت حلقة المتابعة لهذه السلسلة' | Out-Null
Show 'إلغاء اكتمال الحلقة' | Out-Null
Capture 'progress-after-restart.png'
if (-not $alreadyComplete) { TapNode (Show 'إلغاء اكتمال الحلقة') }
Tap 'إغلاق'; TapNode (Show 'مسح حلقة المتابعة')
OpenSeries 'شرح العقيدة الطحاوية'
TapNode (Show 'مسح حلقة المتابعة')
TapNode (Show 'قراءة الكتاب: العقيدة الطحاوية')
WaitReader 'صفحة الملف ' | Out-Null
Jump 3; WaitReader 'صفحة الملف 3 من 16' | Out-Null
ToolbarTap 'شروح هذا الموضع'; Tap 'لا تبلغه الأوهام ولا تدركه الأفهام'
Find '04 لا تبلغه الأوهام ولا تدركه الأفهام' | Out-Null
TapNode (Show 'قراءة الموضع · ص 3 · صفحة الملف 3')
WaitReader 'صفحة الملف 3 من 16' | Out-Null
Capture 'tahawi-page3.png'; Tap '→ العودة'; Tap 'إغلاق'
Write-Output 'PASS second book, per-series resume isolation, restart persistence and reversible manual completion.'

OpenSeries 'شرح الأربعين النووية'
TapNode (Show 'قراءة الكتاب: الأربعون النووية مع زيادة ابن رجب')
WaitReader 'صفحة الملف ' | Out-Null; ToolbarTap 'شروح الكتاب'
Show 'توجد شروح للكتاب، لكن لم تطابق مواضعها مع صفحات هذه النسخة بعد.' | Out-Null
Capture 'unmapped-book.png'
Restart; Tap 'English'; TapNode (Show 'Scholars and lessons')
TapNode (Show 'Choose series'); Tap 'Beneficial means to a happy life commentary'
TapNode (Show 'Read book: الوسائل المفيدة للحياة السعيدة')
WaitReader 'File page ' | Out-Null; ToolbarTap 'Book explanations'
TapNode (Show 'Choose a section or explained passage')
Tap 'Remembering Allah'
TapNode (Show 'Read passage · Printed p. 9 · File page 10')
WaitReader 'File page 10 of 23' | Out-Null
Capture 'english-passage.png'
Restart; Tap 'العربية'
Write-Output 'PASS unmapped-book notice, original lesson availability, English passage selection and printed/file page offset.'

