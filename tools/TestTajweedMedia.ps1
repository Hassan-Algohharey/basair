param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project ('publishing/screenshots/v014-' + $(if ($Serial.StartsWith('emulator-')) { 'emulator' } else { 'phone' }))
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v014-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v014-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v014.png') | Out-Null
    Device @('pull','/sdcard/basair-v014.png',(Join-Path $shots $Name)) | Out-Null
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
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'التجويد')
Search 'idgham'
Show 'دروس تطبيقية مطابقة: 1' | Out-Null
TapNode (Show 'قراءة تحفة الأطفال والغلمان في تجويد القرآن — صفحة الملف 5، ص 26')
WaitReader 'صفحة الملف 5 ' | Out-Null
Capture '01-linked-chapter.png'
Tap '→ العودة'
TapNode (Show 'بداية التطبيق بحسب المصدر: 2:19')
WaitVerse 19 | Out-Null
Capture '02-linked-quran-ayah.png'
Tap '→ العودة'
Show 'دروس تطبيقية مطابقة: 1' | Out-Null
TapNode (Show 'فتح مشغل الدرس')
Show 'المواد الصوتية المتاحة: 1' | Out-Null
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
$before=AudioPosition
Device @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Seconds 6
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Show 'إيقاف مؤقت' | Out-Null
$after=AudioPosition
if ($after -lt $before+4000) { throw "Background did not advance: $before -> $after" }
Tap 'إيقاف مؤقت'
$paused=AudioPosition
Capture '03-practice-player.png'
Restart
TapNode (Show 'التجويد')
TapNode (Show 'متابعة آخر درس اخترته: الإدغام — تطبيقات من البقرة')
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
Tap 'إيقاف مؤقت'
$resumed=AudioPosition
if ($resumed -lt $paused-3000 -or $resumed -gt $paused+20000) { throw "Resume failed: $paused -> $resumed" }
Capture '04-resume-after-restart.png'
TapNode (Show 'إيقاف المشغل')
Device @('shell','input','keyevent','4') | Out-Null
Show 'التجويد' | Out-Null
Restart
TapNode (Show 'التجويد')
TapNode (Show 'شروح تحفة الأطفال والغلمان في تجويد القرآن — الحلقات المفهرسة: 8')
Show 'شرح تحفة الأطفال - الحلقة 1 - مع السفرة - المستوى 2' | Out-Null
TapNode (Show 'المصدر والمشاهدة')
Show 'https://qurantaj.com/media/view-article?id=507' | Out-Null
Capture '05-original-commentary-source.png'
TapNode (Show 'فتح خارج التطبيق')
$browserOpened=$false
for ($i=0;$i -lt 8;$i++) {
    $activity=Device @('shell','dumpsys','activity','activities')
    if ($activity | Select-String '(topResumedActivity|Resumed:).*com.android.chrome') { $browserOpened=$true;break }
    Start-Sleep -Milliseconds 500
}
if (-not $browserOpened) { throw 'Official source did not open in browser' }
Restart
Tap 'البحث'
Search 'Abdul%sQadir'
TapNode (Show 'الشيوخ والدروس')
Show 'عدد المواد: 19' | Out-Null
Capture '06-scholar-search.png'
Restart
Tap 'English'
TapNode (Show 'Tajweed')
Search 'Ahmed%sAmer'
Show 'Matching practice lessons: 5' | Out-Null
Capture '07-english-practice.png'
Restart
Tap 'العربية'
TapNode (Show 'التجويد')
Write-Output "PASS: chapter and Quran anchors, original video source, teacher search, English, background $before -> $after ms and resume $paused -> $resumed ms."
