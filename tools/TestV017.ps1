param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [ValidateSet("All","Books","Comparisons")][string]$Phase="All"
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v017-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v017-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v017-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    $from=if ($Up) { .65 } else { .30 }; $to=if ($Up) { .45 } else { .76 }
    $duration=if ($Up) { '600' } else { '250' }
    Device @('shell','input','swipe',([int]($width*.5)).ToString(),([int]($height*$from)).ToString(),([int]($width*.5)).ToString(),([int]($height*$to)).ToString(),$duration) | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 9;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v017.png') | Out-Null
    Device @('pull','/sdcard/basair-v017.png',(Join-Path $shots $Name)) | Out-Null
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


Restart
if (Lookup 'Home') { Tap 'العربية' }
if ($Phase -in @('All','Books')) {
 Tap 'المكتبة'
 Tap 'الفقه'
 $cases=@(
  @{school='الحنفي (2)';title='المسلك المتقسط في المنسك المتوسط';chapter='الإحرام';page=31;printed='31';total=296;source='الترقي'},
  @{school='المالكي (2)';title='مختصر خليل';chapter='الصلاة';page=17;printed='17';total=248;source='باريس'},
  @{school='الشافعي (2)';title='منهاج الطالبين وعمدة المفتين';chapter='الزكاة';page=32;printed='24';total=147;source='الميمنية'},
  @{school='الحنبلي (2)';title='نيل المآرب بشرح دليل الطالب';chapter='الحج';page=88;printed='ج1، 85';total=323;source='العامرة'}
 )
 foreach ($case in $cases) {
  Top; Tap $case.school
  TapNode (Show $case.title)
  WaitReader 'صفحة الملف ' | Out-Null
  ToolbarTap 'أبواب الكتاب'
  Tap "$($case.chapter) · ص $($case.printed) · صفحة الملف $($case.page)"
  WaitReader "صفحة الملف $($case.page) من $($case.total)" | Out-Null
  Capture "$($case.total)-chapter.png"
  ToolbarTap 'البيانات'
  Find 'بيانات الكتاب والإتاحة' | Out-Null
  if (-not (Ui).OuterXml.Contains($case.source)) { throw "Publisher missing: $($case.title)" }
  Capture "$($case.total)-source.png"; Tap 'إغلاق'
  Jump $case.total
  WaitReader "صفحة الملف $($case.total) من $($case.total)" | Out-Null
  Capture "$($case.total)-last.png"
  Jump $case.page
  WaitReader "صفحة الملف $($case.page) من $($case.total)" | Out-Null
  Tap '→ العودة'
  Write-Output "PASS book $($case.title): school, chapter, edition, last page and return to chapter."
 }
 Restart; Tap 'English'; Tap 'Library'; Tap 'Fiqh'; Tap 'Shafii (2)'
 TapNode (Show 'منهاج الطالبين وعمدة المفتين')
 WaitReader 'File page ' | Out-Null
 ToolbarTap 'Book sections'
 Tap 'Zakat · Printed p. 24 · File page 32'
 WaitReader 'File page 32 of 147' | Out-Null
 Capture 'english-chapter.png'
 Tap 'Back'; Restart; Tap 'العربية'
 Write-Output 'PASS English chapter and printed/file page offset.'
}
if ($Phase -in @('All','Comparisons')) {
 Restart; Tap 'المقارنة'
 Find 'النتائج: 40 / 40' | Out-Null
 Tap 'الطهارة'; Find 'النتائج: 32 / 40' | Out-Null
 Tap 'إعادة الضبط'; Tap 'الصلاة'; Find 'النتائج: 4 / 40' | Out-Null
 Tap 'إعادة الضبط'; Tap 'الزكاة'; Find 'النتائج: 4 / 40' | Out-Null
 $cases=@(
  @('31%s209','نقض ضفائر الشعر في الغسل',31,'209–210',209),
  @('31%s210','الموالاة في غسل البدن',31,'210–211',210),
  @('31%s212','التسمية عند ابتداء الغسل',31,'212–213',212),
  @('31%s214','الوضوء ضمن غسل الجنابة',31,'214',214),
  @('31%s214','وقت غسل الرجلين في غسل الجنابة',31,'214',214),
  @('31%s215','تثليث غسل الأعضاء في الغسل',31,'215',215),
  @('43%s341','إدخال المرفقين في غسل اليدين',43,'341–342',341),
  @('43%s342','موضع الغسل عند قطع اليد من المرفق',43,'342–343',342),
  @('43%s343','غسل الإصبع أو الكف الزائدة',43,'343',343),
  @('43%s365','تجديد الماء لمسح الأذنين',43,'365–366',365),
  @('43%s367','تثليث غسل أعضاء الوضوء',43,'367',367),
  @('43%s367','السواك عند الوضوء',43,'367',367),
  @('43%s368','تقديم اليمنى على اليسرى في الوضوء',43,'368–369',368),
  @('43%s369','إطالة الغرة والتحجيل',43,'369–370',369),
  @('43%s370','استقبال القبلة أثناء الوضوء',43,'370',370)
 )
 foreach ($case in $cases) {
  Top; if (Lookup 'إعادة الضبط') { Tap 'إعادة الضبط' }
  Search $case[0]; Top
  # Reset to the first matching result, then visit the intended issue.
  for ($i=0;$i -lt 12;$i++) {
   $node=Show 'المسألة السابقة'; $button=$node
   while ($button.clickable -ne 'true' -and $button.ParentNode.Name -eq 'node') {$button=$button.ParentNode}
   if ($button.enabled -ne 'true') { break }; TapNode $button; Top
  }
  $found=$false
  for ($i=0;$i -lt 12;$i++) {
   if (Lookup $case[1]) { $found=$true;break }
   TapNode (Show 'المسألة التالية'); Top
  }
  if (-not $found) {throw "Issue missing: $($case[1])"}
  TapNode (Show "قراءة المرجع داخل التطبيق · صفحة $($case[4])")
  Find "الموسوعة الفقهية الكويتية · ج $($case[2]) · ص $($case[3])" | Out-Null
  Capture "comparison-$($case[2])-$($case[4])-$($cases.IndexOf($case)).png"
  Tap 'إغلاق'
  Write-Output "PASS comparison $($case[1]) / volume $($case[2]) pages $($case[3])."
 }
 Top; Tap 'إعادة الضبط'; Search '43%s368'; Tap 'الحنفي'
 Find 'النتائج: 1 / 40' | Out-Null
 TapNode (Show 'قراءة المرجع داخل التطبيق · صفحة 368')
 if (Lookup 'فتح صفحة 368') { Tap 'فتح صفحة 368' } else { Tap 'تنزيل المجلد وفتح الصفحة' }
 WaitReader 'صفحة الملف 368 من 450' | Out-Null
 Capture 'reference-43-page368.png'
 Tap '→ العودة'
 Restart; Tap 'English'; Tap 'Compare'
 Find 'Results: 40 / 40' | Out-Null; Capture 'comparisons-en.png'
 Restart; Tap 'العربية'
 Write-Output 'PASS all new comparisons, topic counts, combined school/page filter and English controls.'
}
