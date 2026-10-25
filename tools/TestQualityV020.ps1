param(
 [Parameter(Mandatory=$true)][string]$Serial,
 [string]$Adb='C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
 [ValidateSet('All','Filters','ReaderAudio','LargeText')][string]$Phase='All'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v020-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v020-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v020-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
function Top { Ui | Out-Null; 1..8 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 20;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v020.png') | Out-Null
    Device @('pull','/sdcard/basair-v020.png',(Join-Path $shots $Name)) | Out-Null
}
function Restart {
    Device @('shell','am','force-stop',$package) | Out-Null
    Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
}
function Search([string]$Query) {
    Top
    $field=$null
    for ($i=0;$i -lt 12;$i++) {
        $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
        if ($field) {break}
        Swipe $true
    }
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

function ArabicHome {
 Restart
 if (Lookup 'Home') { Tap 'العربية' }
}
function InputText([string]$Text,[bool]$Replace=$false) {
 $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
 if (-not $field) {throw 'No input field'}
 TapNode $field
 if ($Replace) {
  Device @('shell','input','keyevent','123') | Out-Null
  for ($n=0;$n -lt $field.text.Length+2;$n++) {Device @('shell','input','keyevent','67') | Out-Null}
 }
 Device @('shell','input','text',$Text) | Out-Null
 Device @('shell','input','keyevent','4') | Out-Null
 Find ($Text.Replace('%s',' ')) | Out-Null
}
function SaveReport {
 Tap 'حفظ البلاغ محليًا'
 for ($i=0;$i -lt 6;$i++) {if (-not (Lookup 'حفظ البلاغ محليًا')) {return}}
 throw 'Report editor did not close after saving'
}
function Near([string]$Text,[string]$Action) {
 $node=Show $Text
 while ($node.Name -eq 'node') {
  $button=$node.SelectNodes('.//node') | Where-Object {$_.text -eq $Action} | Select-Object -First 1
  if ($button) {TapNode $button;return}
  $node=$node.ParentNode
 }
 throw "No action $Action for $Text"
}
function AudioPosition {
 $line=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.text.StartsWith('موضع الاستماع:')} | Select-Object -First 1
 if (-not $line) {throw 'Listening position not visible'}
 $clock=[regex]::Match($line.text,'(\d+):(\d+):(\d+)')
 return ([int64]$clock.Groups[1].Value*3600+[int64]$clock.Groups[2].Value*60+[int64]$clock.Groups[3].Value)*1000
}


if ($Phase -in @('All','Filters')) {
ArabicHome; TapNode (Show 'الشيوخ والدروس')
TapNode (Show 'تصفية بسنة النشر الهجرية'); TapNode (Show 'سنة 1434 هـ')
Show 'عدد المواد: 7' | Out-Null
Capture 'year1434.png'
ArabicHome; TapNode (Show 'الشيوخ والدروس'); Search 'Othman%sTuhfat%s1'
TapNode (Show 'المصدر والمشاهدة')
Find 'المدة: 56:51' | Out-Null
Find 'تاريخ النشر بالمصدر: ١٦ أكتوبر ٢٠١٩' | Out-Null
if (-not (Contains 'كان الرابط متاحًا عند الفحص بتاريخ 2026-10-25')) {throw 'Source check date absent'}
Capture 'verified-source-duration.png'; Tap 'إغلاق'
ArabicHome; TapNode (Show 'الشيوخ والدروس')
TapNode (Show 'تصفية بمدة التسجيل'); Tap 'طويل — أكثر من30 دقيقة'
Show 'عدد المواد: 18' | Out-Null
Capture 'long-filter.png'
Write-Output 'PASS new 1434 year filter; original item date and embedded-player duration; source check date and long-duration filter.'

}
if ($Phase -in @('All','ReaderAudio')) {
ArabicHome; TapNode (Show 'بحث داخل متن التحفة'); InputText '42'; Tap 'قراءة الموضع · ص 35 · صفحة الملف 14'
WaitReader 'صفحة الملف 14 من 18' | Out-Null
Tap 'تكبير الصفحة'; Find 'نسبة التكبير 150 بالمئة' | Out-Null
Tap 'تصغير الصفحة'; Find 'نسبة التكبير 100 بالمئة' | Out-Null
Capture 'reader-zoom-accessibility.png'
Write-Output 'PASS named zoom actions and announced zoom value on the physical reader.'

ArabicHome; TapNode (Show 'المكتبة الصوتية'); Search 'Hashim'
TapNode (Show 'تحفة الأطفال — قراءة صلاح الهاشم على الأبواب')
Show 'تكرار التسجيل كاملًا' | Out-Null
$toggle=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.checkable -eq 'true'} | Select-Object -First 1
if (-not $toggle) {throw 'Repeat merged checkbox missing'}
$oldRepeat=$toggle.checked
TapNode $toggle
$changed=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.checkable -eq 'true'} | Select-Object -First 1
if ($changed.checked -eq $oldRepeat) {throw 'Repeat row did not toggle'}
TapNode $changed
$restored=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.checkable -eq 'true'} | Select-Object -First 1
if ($restored.checked -ne $oldRepeat) {throw 'Repeat state not restored'}
Top
Show 'موضع الاستماع' | Out-Null
Capture 'audio-accessibility.png'
Write-Output 'PASS listening slider label and merged repeat checkbox; original repeat choice restored.'

}
if ($Phase -in @('All','LargeText')) {
$font=(Device @('shell','settings','get','system','font_scale') | Out-String).Trim()
try {
 Device @('shell','settings','put','system','font_scale','2.0') | Out-Null
 Restart; Tap 'English'; TapNode (Show 'Scholars and lessons')
 Search 'Othman%sTuhfat%s1'; TapNode (Show 'Source and viewing')
 Show 'Duration: 56:51' | Out-Null
 Swipe $true
 Find 'Open externally' | Out-Null
 Find 'Close' | Out-Null
 Capture 'english-font200-source.png'; Tap 'Close'
 Write-Output 'PASS English media metadata at 200 percent system font scale.'
} finally {
 Device @('shell','settings','put','system','font_scale',$font) | Out-Null
 Restart
 if (Lookup 'Home') {Tap 'العربية'}
}

}
