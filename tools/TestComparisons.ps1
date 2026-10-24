param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
# This test only updates/interacts with the separate preview app; it never clears app data.
$package = 'com.algohary.basair.preview'
$project = Split-Path $PSScriptRoot -Parent
$shots = Join-Path $project 'publishing/screenshots/v06-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Read-Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-comparisons-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-comparisons-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
    [xml](Get-Content (Join-Path $shots 'ui.xml') -Raw -Encoding UTF8)
}
function Lookup([string]$Text) {
    (Read-Ui).SelectNodes('//node') | Where-Object {
        $_.package -eq $package -and ($_.text -eq $Text -or $_.'content-desc' -eq $Text)
    } | Select-Object -First 1
}
function Find([string]$Text) {
    for ($attempt=0; $attempt -lt 3; $attempt++) { $node=Lookup $Text; if ($node) { return $node } }
    throw "Element missing: $Text"
}
function TapNode($Node) {
    while ($Node.clickable -ne 'true' -and $Node.ParentNode.Name -eq 'node') { $Node=$Node.ParentNode }
    $b=[regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Device @('shell','input','tap',([int](($b[0]+$b[2])/2)).ToString(),([int](($b[1]+$b[3])/2)).ToString()) | Out-Null
    Start-Sleep -Milliseconds 300
}
function Tap([string]$Text) { TapNode (Find $Text) }
$size=Device @('shell','wm','size')
$match=[regex]::Match(($size -join ' '),'(\d+)x(\d+)')
if (-not $match.Success) { throw 'Cannot determine viewport size' }
$width=[int]$match.Groups[1].Value; $height=[int]$match.Groups[2].Value
function Swipe([bool]$Up) {
    $from=if ($Up) { .76 } else { .32 }; $to=if ($Up) { .32 } else { .76 }
    Device @('shell','input','swipe',([int]($width*.5)).ToString(),([int]($height*$from)).ToString(),([int]($width*.5)).ToString(),([int]($height*$to)).ToString(),'250') | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($attempt=0; $attempt -lt 9; $attempt++) {
        $node=Lookup $Text; if ($node) { return $node }; Swipe $true
    }
    throw "Scrolled element missing: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-comparisons.png') | Out-Null
    Device @('pull','/sdcard/basair-comparisons.png',(Join-Path $shots $Name)) | Out-Null
}
function Search([string]$Query) {
    $field=(Read-Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
    if (-not $field) { throw 'Search field missing' }
    TapNode $field
    Device @('shell','input','text',$Query) | Out-Null
    Device @('shell','input','keyevent','111') | Out-Null
}
Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المقارنة'
Find 'النتائج: 15 من 15 مسألة' | Out-Null
Capture '01-comparison-search.png'
Tap 'الصلاة'
Find 'النتائج: 4 من 15 مسألة' | Out-Null
Tap 'الحنفي'
Search '274'
Find 'النتائج: 1 من 15 مسألة' | Out-Null
Show 'المذهب الحنفي' | Out-Null
Show 'الموسوعة الفقهية الكويتية · ج 27 · ص 274' | Out-Null
Capture '02-combined-filters-source.png'
Top
Tap 'إعادة الضبط'
Search 'missingquery'
Find 'النتائج: 0 من 15 مسألة' | Out-Null
Find 'لا توجد مسائل مطابقة. جرّب كلمة أقصر أو غيّر الباب أو المذهب، أو اضغط إعادة الضبط.' | Out-Null
Capture '03-no-results.png'
Tap 'إعادة الضبط'
Find 'النتائج: 15 من 15 مسألة' | Out-Null
Tap 'الزكاة'
Find 'النتائج: 4 من 15 مسألة' | Out-Null
Tap 'الطهارة'
Find 'النتائج: 7 من 15 مسألة' | Out-Null
Tap 'إعادة الضبط'
$cases=@(
    @('النية في الغسل من الجنابة','31','207'),
    @('الترتيب في الوضوء','43','355–356'),
    @('الموالاة في الوضوء','43','355'),
    @('الدلك في الغسل','31','211–212'),
    @('المضمضة والاستنشاق في الغسل','31','208–209'),
    @('القدر المجزئ من مسح الرأس','43','348–349'),
    @('المضمضة والاستنشاق في الوضوء','43','360'),
    @('حكم قصر الصلاة في السفر','27','274'),
    @('اقتداء المسافر بالمقيم','27','280–281'),
    @('قضاء فائتة السفر في الحضر','27','281'),
    @('مدة الإقامة المانعة من القصر','27','282–283'),
    @('الزكاة في مال الصغير والمجنون','23','232'),
    @('تعجيل الزكاة قبل الحول','23','294–295'),
    @('تأخير إخراج الزكاة بعد وجوبها','23','295–296'),
    @('إخراج القيمة في الزكاة','23','299')
)
for ($i=0; $i -lt $cases.Count; $i++) {
    $case=$cases[$i]
    Top
    Show $case[0] | Out-Null
    Show "الموسوعة الفقهية الكويتية · ج $($case[1]) · ص $($case[2])" | Out-Null
    Write-Output "PASS issue $($i+1)/15: $($case[0]); volume $($case[1]), pages $($case[2])"
    if ($i -in @(5,10,14)) { Capture "issue-$($i+1)-reference.png" }
    if ($i -lt $cases.Count-1) { Top; TapNode (Show 'المسألة التالية') }
}
Top
Tap 'الحنفي'
Show 'المذهب الحنفي' | Out-Null
Top
Tap 'المالكي'
Show 'المذهب المالكي' | Out-Null
Top
Tap 'الشافعي'
Show 'المذهب الشافعي' | Out-Null
Top
# Reveal the last chip in its horizontal row when necessary.
if (-not (Lookup 'الحنبلي')) {
    $chip=Find 'الشافعي'; $b=[regex]::Matches($chip.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $y=([int](($b[1]+$b[3])/2)).ToString()
    Device @('shell','input','swipe','120',$y,([int]($width*.85)).ToString(),$y,'250') | Out-Null
}
Tap 'الحنبلي'
Show 'المذهب الحنبلي' | Out-Null
Capture '04-school-filter.png'
Top
Tap 'إعادة الضبط'
Find 'النتائج: 15 من 15 مسألة' | Out-Null
Write-Output 'PASS v0.6.0: fifteen issue selections and displayed source pages; search, combined filters, 7/4/4 topics, four school filters, empty results and reset. Preview data retained.'
