param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [switch]$ChangedOnly
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v083-comparisons-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v083-comparisons-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v083-comparisons-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v083-comparisons.png') | Out-Null
    Device @('pull','/sdcard/basair-v083-comparisons.png',(Join-Path $shots $Name)) | Out-Null
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
Tap 'المقارنة'
Find 'النتائج: 25 / 25' | Out-Null
Tap 'الطهارة'
Find 'النتائج: 17 / 25' | Out-Null
Tap 'إعادة الضبط'
Tap 'الصلاة'
Find 'النتائج: 4 / 25' | Out-Null
Tap 'إعادة الضبط'
Tap 'الزكاة'
Find 'النتائج: 4 / 25' | Out-Null
Tap 'إعادة الضبط'
$cases=@(
 @('357','الدلك في الوضوء','357',0),
 @('358','التسمية في أول الوضوء','358',1),
 @('351','عدد مرات مسح الرأس','351',0),
 @('350','غسل الرأس بدل مسحه','350',0),
 @('350','حلق شعر الرأس بعد الوضوء','350',1),
 @('364','حكم مسح الأذنين','364',0),
 @('360','الاستنثار في الوضوء','360',1),
 @('362','كيفية مسح الرأس المسنونة','362',0),
 @('360','غسل اليدين الطاهرتين أول الوضوء','360',2),
 @('352','غسل الرجلين المكشوفتين والكعبين','352–354',0)
)
if ($ChangedOnly) { $cases=@($cases | Where-Object { $_[0] -eq '360' }) }
foreach ($case in $cases) {
 Top
 if (Lookup 'إعادة الضبط') { Tap 'إعادة الضبط' }
 Search $case[0]
 Top
 for ($rewind=0; $rewind -lt 3; $rewind++) {
  $previous=Show 'المسألة السابقة'
  $button=$previous
  while ($button.clickable -ne 'true' -and $button.ParentNode.Name -eq 'node') { $button=$button.ParentNode }
  if ($button.enabled -ne 'true') { break }
  TapNode $button; Top
 }
 for ($step=0; $step -lt $case[3]; $step++) { TapNode (Show 'المسألة التالية'); Top }
 Find $case[1] | Out-Null
 Show "الموسوعة الفقهية الكويتية · ج 43 · ص $($case[2])" | Out-Null
 $filePage=($case[2] -split '–')[0]
 TapNode (Show "قراءة المرجع داخل التطبيق · صفحة $filePage")
 Find 'الطبعة الأولى، 1425هـ / 2005م' | Out-Null
 Capture "source-$($case[0])-$($case[3]).png"
 Tap 'إغلاق'
 Write-Output ('PASS new issue: '+$case[1]+' / vol 43 pages '+$case[2])
}
Top
Tap 'إعادة الضبط'
Search '357'
Tap 'الحنفي'
Find 'النتائج: 1 / 25' | Out-Null
TapNode (Show 'قراءة المرجع داخل التطبيق · صفحة 357')
Tap 'فتح صفحة 357'
WaitReader 'صفحة الملف 357 من 450' | Out-Null
Capture '11-reference-exact-page.png'

ToolbarTap 'ملاحظاتي'
Tap 'كتابة أو تعديل ملاحظة الصفحة 357'
$field=(Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.package -eq $package } | Select-Object -First 1
if ($field.text -ne '') { throw 'Reference test page already has a note; preserve it.' }
TapNode $field
Device @('shell','input','text','Basair083ReferenceNote') | Out-Null
Device @('shell','input','keyevent','4') | Out-Null
Tap 'حفظ الملاحظة'
Restart
Tap 'ملاحظاتي'
Find 'Basair083ReferenceNote' | Out-Null
Tap 'فتح الصفحة'
Find 'الموسوعة الفقهية الكويتية · ج 43 · صفحة الملف 357' | Out-Null
Tap 'فتح صفحة 357'
WaitReader 'صفحة الملف 357 من 450' | Out-Null
ToolbarTap 'ملاحظاتي'
Tap 'تعديل'
Tap 'حذف الملاحظة'
Tap 'حذف الملاحظة'
Tap 'إغلاق'
Write-Output 'PASS reference note reopened the exact edition and file page, then test note removed.'

Tap '→ العودة'
Restart
Tap 'English'
Tap 'Compare'
Find 'Results: 25 / 25' | Out-Null
Capture '12-comparisons-en.png'
Restart
Tap 'العربية'
Write-Output ('PASS topic counts, '+$cases.Count+' selected source cards, combined school/page search, exact reference page and English comparison controls.')
