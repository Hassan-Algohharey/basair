param(
 [Parameter(Mandatory=$true)][string]$Serial,
 [string]$Adb='C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
 [ValidateSet('All','Bookmarks','Timer','EndTimer','Series','LargeText')][string]$Phase='All'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v021-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v021-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v021-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v021.png') | Out-Null
    Device @('pull','/sdcard/basair-v021.png',(Join-Path $shots $Name)) | Out-Null
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



function OpenReading {
 ArabicHome; TapNode (Show 'المكتبة الصوتية'); Search 'alansari'
 TapNode (Show 'الأربعون النووية مع زيادة ابن رجب — قراءة إبراهيم الأنصاري')
}
if ($Phase -in @('All','Bookmarks')) {
 OpenReading
 TapNode (Show 'حفظ علامة صوتية هنا'); InputText 'v021%sbookmark'
 Tap 'حفظ العلامة الصوتية'
 TapNode (Show 'العلامات الصوتية لهذا العمل'); Find 'v021 bookmark' | Out-Null
 Capture 'bookmark-created.png'; Tap 'إغلاق'
 OpenReading; TapNode (Show 'العلامات الصوتية لهذا العمل')
 Find 'v021 bookmark' | Out-Null
 Near 'v021 bookmark' 'تعديل العلامة'; InputText 'v021%sedited' $true; Tap 'حفظ العلامة الصوتية'
 Find 'v021 edited' | Out-Null
 Near 'v021 edited' 'الانتقال إلى الموضع'
 Show 'تشغيل' | Out-Null
 if (Lookup 'إيقاف مؤقت') {throw 'Bookmark unexpectedly started playback'}
 TapNode (Show 'العلامات الصوتية لهذا العمل')
 Near 'v021 edited' 'حذف العلامة'; Tap 'إغلاق'; Find 'v021 edited' | Out-Null
 Near 'v021 edited' 'حذف العلامة'; Tap 'حذف العلامة'
 if (Lookup 'v021 edited') {throw 'Deleted test bookmark still visible'}
 Capture 'bookmark-deleted.png'; Tap 'إغلاق'
 Write-Output 'PASS audio bookmark creation, restart, editing, jump without autoplay, cancel-delete and deletion; diagnostic bookmark removed.'
}
if ($Phase -in @('All','Timer')) {
 OpenReading
 Show 'نسخة محفوظة متاحة دون اتصال' | Out-Null
 $wifi=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
 $data=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
 try {
  Device @('shell','svc','wifi','disable') | Out-Null; Device @('shell','svc','data','disable') | Out-Null
  TapNode (Show 'تشغيل'); Show 'إيقاف مؤقت' | Out-Null
  $before=AudioPosition
  TapNode (Show 'مؤقت إيقاف الاستماع')
  InputText '1' $true; Tap 'بدء المؤقت'
  Device @('shell','input','keyevent','3') | Out-Null
  Start-Sleep -Seconds 35
  Start-Sleep -Seconds 30
  Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
  Show 'لا يوجد مؤقت نشط' | Out-Null
  $after=AudioPosition
  if ($after -le $before) {throw 'Offline/background playback did not advance'}
  Start-Sleep -Seconds 3
  $stable=AudioPosition
  if ($stable -ne $after) {throw 'Timer did not pause background playback'}
  Show 'تشغيل' | Out-Null
  TapNode (Show 'مؤقت إيقاف الاستماع'); InputText '1' $true; Tap 'بدء المؤقت'
  OpenReading; Show 'لا يوجد مؤقت نشط' | Out-Null
  Capture 'timer-cleared-after-restart.png'
  TapNode (Show 'إيقاف المشغل')
  Write-Output "PASS retained offline audio/background timer: $before -> $after; pause stable and timer cleared across process restart."
 } finally {
  if ($wifi -eq '1') {Device @('shell','svc','wifi','enable') | Out-Null}
  if ($data -eq '1') {Device @('shell','svc','data','enable') | Out-Null}
 }
}

if ($Phase -in @('All','EndTimer')) {
 ArabicHome; TapNode (Show 'المكتبة الصوتية'); Search 'without%sghunnah'
 TapNode (Show 'الإدغام بغير غنة: يَكُن لَّهُ')
 TapNode (Show 'تشغيل'); Device @('shell','input','keyevent','127') | Out-Null
 $label=Show 'تكرار المثال فقط'
 $box=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.checkable -eq 'true'} | Select-Object -First 1
 $oldRepeat=$box.checked -eq 'true'
 try {
  Toggle 'تكرار المثال فقط' $true
  TapNode (Show 'مؤقت إيقاف الاستماع'); Tap 'الإيقاف عند نهاية الفصل الحالي'
  TapNode (Show 'تشغيل'); Start-Sleep -Seconds 9
  Show 'لا يوجد مؤقت نشط' | Out-Null; Show 'تشغيل' | Out-Null
  Capture 'end-timer-overrides-repeat.png'
  Write-Output 'PASS end-of-chapter timer pauses the original 4.4-second example even with repeat enabled.'
 } finally {
  Device @('shell','input','keyevent','127') | Out-Null
  if (Lookup 'إغلاق') {Tap 'إغلاق'}
  Toggle 'تكرار المثال فقط' $oldRepeat
  TapNode (Show 'إيقاف المشغل')
 }
}

if ($Phase -in @('All','Series')) {
 ArabicHome; TapNode (Show 'الشيوخ والدروس'); TapNode (Show 'اختيار سلسلة'); Tap 'شرح العقيدة الطحاوية'
 Show 'الحلقات المفهرسة: 32' | Out-Null
 Search 'index%s16'; Show 'ترتيب الفهرس: 16 · رقم المصدر: 15' | Out-Null
 Capture 'duplicate-source15.png'
 TapNode (Show 'المصدر والمشاهدة'); Find '15 أصول المعتزلة الفاسدة' | Out-Null
 Tap 'المادة السابقة'; Find '15 الإيمان بعلو الله سبحانه' | Out-Null
 Capture 'duplicate-source15-navigation.png'; Tap 'إغلاق'
 Write-Output 'PASS full source-index count 32, two distinct source-number 15 lessons and adjacent navigation.'
}
if ($Phase -in @('All','LargeText')) {
 $font=(Device @('shell','settings','get','system','font_scale') | Out-String).Trim()
 try {
  Device @('shell','settings','put','system','font_scale','2.0') | Out-Null
  Restart; Tap 'English'; TapNode (Show 'Audio library'); Search 'alansari'
  TapNode (Show 'الأربعون النووية مع زيادة ابن رجب — قراءة إبراهيم الأنصاري')
  TapNode (Show 'Sleep timer'); Show 'Cancel timer' | Out-Null; Capture 'timer-font200.png'; Tap 'Close'
  TapNode (Show 'Bookmark this listening position'); InputText 'v021%sdraft'
  Device @('shell','settings','put','system','font_scale','1.5') | Out-Null
  Find 'v021 draft' | Out-Null
  Device @('shell','input','keyevent','4') | Out-Null
  if (-not (Lookup 'Discard unsaved changes?')) {Device @('shell','input','keyevent','4') | Out-Null}
  Find 'Discard unsaved changes?' | Out-Null; Tap 'Continue editing'; Find 'v021 draft' | Out-Null
  Device @('shell','input','keyevent','4') | Out-Null
  if (-not (Lookup 'Discard unsaved changes?')) {Device @('shell','input','keyevent','4') | Out-Null}
  Tap 'Discard changes'
  Device @('shell','settings','put','system','font_scale','2.0') | Out-Null
  TapNode (Show 'Audio bookmarks for this work'); Show 'Personal bookmarks stored on this phone. Going to a position pauses audio and prepares the position without starting playback. Example times are relative to the clipped example.' | Out-Null
  Capture 'marks-font200.png'; Tap 'Close'
  Write-Output 'PASS English timer/bookmarks at 200 percent font; draft survives font configuration change, continue-editing and discard without saving.'
 } finally {
  Device @('shell','settings','put','system','font_scale',$font) | Out-Null
  Restart; if (Lookup 'Home') {Tap 'العربية'}
 }
}
