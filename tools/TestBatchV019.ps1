param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [ValidateSet('All','SearchReports','Media','MediaFilters','Audio','Madinah','MadinahResume','LargeText')][string]$Phase='All'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v019-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v019-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v019-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v019.png') | Out-Null
    Device @('pull','/sdcard/basair-v019.png',(Join-Path $shots $Name)) | Out-Null
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

if ($Phase -in @('All','SearchReports')) {
 ArabicHome; TapNode (Show 'بحث داخل متن التحفة')
 InputText '42'
 Find 'البيت 42' | Out-Null
 Capture 'text-result42.png'
 Tap 'قراءة الموضع · ص 35 · صفحة الملف 14'
 WaitReader 'صفحة الملف 14 من 18' | Out-Null
 Capture 'text-result-page14.png'
 ToolbarTap 'الإبلاغ عن خطأ في المحتوى'
 InputText 'v019%stest%sreport'
 SaveReport
 ArabicHome; TapNode (Show 'بلاغات المحتوى المحفوظة')
 Near 'v019 test report' 'تعديل'
 InputText 'v019%supdated%sreport' $true
 SaveReport
 ArabicHome; TapNode (Show 'بلاغات المحتوى المحفوظة')
 Show 'v019 updated report' | Out-Null; Capture 'report-persisted.png'
 Near 'v019 updated report' 'مشاركة البلاغ باختيارك'
 $chooser=$false
 for ($i=0;$i -lt 8;$i++) {if ((Ui).SelectNodes('//node') | Where-Object {$_.package -eq 'com.android.intentresolver'}) {$chooser=$true;break}}
 if (-not $chooser) {throw 'Share chooser did not open'}
 Device @('shell','input','keyevent','4') | Out-Null
 Find 'v019 updated report' | Out-Null
 Near 'v019 updated report' 'حذف'; Tap 'إغلاق'
 Show 'v019 updated report' | Out-Null
 Near 'v019 updated report' 'حذف'; Tap 'حذف'
 if (Lookup 'v019 updated report') {throw 'Report deletion failed'}
 TapNode (Show 'الإبلاغ عن خطأ في المحتوى')
 InputText 'v019%sdiscard'
 Tap 'إغلاق'; Find 'تجاهل التعديلات غير المحفوظة؟' | Out-Null
 Tap 'استكمال الكتابة'; Find 'v019 discard' | Out-Null
 Tap 'إغلاق'; Tap 'تجاهل'
 Write-Output 'PASS full-text verse search to original page; contextual local report, edit/restart/share chooser/cancel delete/delete and unsaved draft protection.'
}

if ($Phase -in @('All','Media')) {
 ArabicHome; TapNode (Show 'الشيوخ والدروس'); TapNode (Show 'اختيار سلسلة'); Tap 'شرح الأربعين النووية'
 Show 'الحلقات المفهرسة: 43' | Out-Null
 Search 'lesson%s43'
 Show 'عدد المواد: 1' | Out-Null; TapNode (Show 'المصدر والمشاهدة')
 Find '(43) الدرس الثالث والأربعون' | Out-Null
 Tap 'المادة السابقة'; Find '(42) الدرس الثاني والأربعون' | Out-Null
 Tap 'المادة التالية'; Find '(43) الدرس الثالث والأربعون' | Out-Null
 Capture 'series43-last.png'; Tap 'إغلاق'
 Write-Output 'PASS all 43 source-indexed episodes and final episode navigation.'
}
if ($Phase -in @('All','Media','MediaFilters')) {
 ArabicHome; TapNode (Show 'الشيوخ والدروس')
 TapNode (Show 'تصفية بسنة النشر الهجرية'); Tap 'سنة 1435 هـ'
 Show 'عدد المواد: 43' | Out-Null
 Capture 'year1435.png'
 ArabicHome; TapNode (Show 'الشيوخ والدروس')
 TapNode (Show 'تصفية بمدة التسجيل'); Tap 'قصير — حتى 3 دقائق'
 Show 'عدد المواد: 1' | Out-Null
 Show 'خزائن الله تغني كل مفتقر' | Out-Null
 Show 'المدة بالمصدر: 59 ثانية' | Out-Null
 TapNode (Show 'المصدر والمشاهدة')
 Find 'خزائن الله تغني كل مفتقر' | Out-Null
 if (-not (Contains 'https://t.me/Ahmed_Elaraby/2304')) {throw 'Original short-video URL missing'}
 Capture 'short-original-source.png'; Tap 'إغلاق'
 Write-Output 'PASS Hijri publication year and documented short-duration/source filters.'
}

if ($Phase -in @('All','Audio')) {
 ArabicHome; TapNode (Show 'المكتبة الصوتية'); Search 'Hashim'
 TapNode (Show 'تحفة الأطفال — قراءة صلاح الهاشم على الأبواب')
 TapNode (Show 'اختيار فصل مسموع'); Tap '2. أحكام النون الساكنة والتنوين'
 TapNode (Show 'قراءة الموضع · ص 26 · صفحة الملف 5')
 WaitReader 'صفحة الملف 5 من 18' | Out-Null
 Capture 'audio-chapter-page5.png'; Tap '→ العودة'
 TapNode (Show 'تشغيل'); Show 'إيقاف مؤقت' | Out-Null
 $before=AudioPosition
 Device @('shell','input','keyevent','3') | Out-Null
 Start-Sleep -Seconds 6
 Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
 Show 'إيقاف مؤقت' | Out-Null; $after=AudioPosition
 if ($after -lt $before+2000) {throw "Background audio stalled: $before -> $after"}
 Tap 'إيقاف مؤقت'; $paused=AudioPosition
 Capture 'audio-background.png'
 ArabicHome; TapNode (Show 'المكتبة الصوتية')
 Show 'أحكام النون الساكنة والتنوين' | Out-Null
 TapNode (Show 'تشغيل'); Show 'إيقاف مؤقت' | Out-Null; Tap 'إيقاف مؤقت'
 $resumed=AudioPosition
 if ($resumed -lt $paused-3000 -or $resumed -gt $paused+15000) {throw 'Chapter resume position mismatch'}
 TapNode (Show 'إيقاف المشغل')
 TapNode (Show 'اختيار فصل مسموع'); Tap '7. أقسام المد'
 Show 'قراءة الموضع · ص 33 · صفحة الملف 12' | Out-Null
 Capture 'audio-chapter7.png'
 Write-Output "PASS chapter selection, exact section page, background advance $before -> $after, saved position $paused -> $resumed and last chapter."
}

if ($Phase -in @('All','Madinah')) {
 ArabicHome; TapNode (Show 'القرآن الكريم والتفسير الميسر')
 ToolbarTap 'مصحف المدينة المصوّر'
 WaitReader 'صفحة الملف ' | Out-Null
 Jump 1
 WaitReader 'صفحة الملف 1 من 604' | Out-Null
 Capture 'madinah-first.png'
 ToolbarTap 'التنزيل والتخزين'
 $ready=$false
 for ($i=0;$i -lt 16;$i++) {
  $node=Contains 'صفحات الملف المحفوظة:'
  if ($node -and [regex]::Match($node.text,'(?<!\d)1–(\d+)').Success -and [int]([regex]::Match($node.text,'(?<!\d)1–(\d+)').Groups[1].Value) -ge 8) {$ready=$true;break}
  Tap 'إغلاق'; Start-Sleep -Milliseconds 500; ToolbarTap 'التنزيل والتخزين'
 }
 if (-not $ready) {throw 'First eight image pages not cached in time'}
 Capture 'madinah-page-cache.png'; Tap 'إغلاق'
 $wifi=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
 $data=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
 try {
  Device @('shell','svc','wifi','disable') | Out-Null; Device @('shell','svc','data','disable') | Out-Null
  Jump 8; WaitReader 'صفحة الملف 8 من 604' | Out-Null
  Capture 'madinah-offline8.png'
 } finally {
  if ($wifi -eq '1') {Device @('shell','svc','wifi','enable') | Out-Null}
  if ($data -eq '1') {Device @('shell','svc','data','enable') | Out-Null}
 }
 Start-Sleep -Seconds 3
 Jump 121; WaitReader 'صفحة الملف 121 من 604' | Out-Null
 Capture 'madinah121.png'; ToolbarTap 'التفسير لهذه الصفحة'
 Find 'تفسير آيات الصفحة المصورة 121' | Out-Null
 for ($i=0;$i -lt 12;$i++) {if (Contains '5:78') {break}}
 if (-not (Contains '5:78')) {throw 'Printed-page tafsir first verse missing'}
 if (Contains '5:77') {throw 'Adjacent textual-index verse leaked into printed-page tafsir'}
 Capture 'madinah121-tafsir.png'; Tap 'إغلاق'
 Jump 604; WaitReader 'صفحة الملف 604 من 604' | Out-Null
 Capture 'madinah604.png'; ToolbarTap 'التفسير لهذه الصفحة'
 Find 'تفسير آيات الصفحة المصورة 604' | Out-Null
 for ($i=0;$i -lt 12;$i++) {if (Contains '112:1') {break}}
 if (-not (Contains '112:1')) {throw 'Last printed page tafsir missing'}
 Capture 'madinah604-tafsir.png'; Tap 'إغلاق'
 Write-Output 'PASS official image pages 1/8/121/604, adjacent-page cache and offline reading, and printed-page tafsir excluding neighboring index verses.'
}

if ($Phase -in @('All','MadinahResume')) {
 ArabicHome; TapNode (Show 'القرآن الكريم والتفسير الميسر')
 ToolbarTap 'مصحف المدينة المصوّر'; WaitReader 'صفحة الملف ' | Out-Null
 foreach ($target in @(121,604)) {
  Jump $target; WaitReader "صفحة الملف $target من 604" | Out-Null
  Restart; TapNode (Show 'القرآن الكريم والتفسير الميسر')
  ToolbarTap 'مصحف المدينة المصوّر'
  WaitReader "صفحة الملف $target من 604" | Out-Null
  Capture "madinah-resume$target.png"
 }
 Write-Output 'PASS exact printed-page resume at 121 and 604 after process restart, using saved verse anchors rather than textual-group numbers.'
}

if ($Phase -in @('All','LargeText')) {
 $font=(Device @('shell','settings','get','system','font_scale') | Out-String).Trim()
 try {
  Device @('shell','settings','put','system','font_scale','1.3') | Out-Null
  Restart
  if (Lookup 'الرئيسية') {Tap 'English'}
  TapNode (Show 'Search the Tuhfah text'); InputText '61'
  Find 'Verse 61' | Out-Null; Capture 'large-text-search-en.png'; Tap 'Close'
  TapNode (Show 'Saved content reports'); Show 'Report a content issue' | Out-Null
  Capture 'large-text-reports-en.png'
 } finally {
  if ($font -match '^\d+(\.\d+)?$') {Device @('shell','settings','put','system','font_scale',$font) | Out-Null}
  ArabicHome
 }
 Write-Output 'PASS English text search/reports at 130% font scale; original font setting and Arabic restored.'
}
