param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
# This test only updates/interacts with the separate preview app; it never clears app data.
$package = 'com.algohary.basair.preview'
$project = Split-Path $PSScriptRoot -Parent
$shots = Join-Path $project 'publishing/screenshots/v07-phone'
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
    Device @('shell','input','keyevent','4') | Out-Null
}

function WaitReader([int]$Volume,[int]$Page,[int]$Pages) {
    $image="صفحة الملف $Page من $Pages · الموسوعة الفقهية الكويتية — الجزء $Volume"
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        $ui=Read-Ui
        $nodes=@($ui.SelectNodes('//node') | Where-Object { $_.package -eq $package })
        if ($nodes | Where-Object { $_.'content-desc' -eq $image }) { return }
        if ($nodes | Where-Object { $_.text -match '^تعذر التنزيل|^تعذر فتح نسخة|^تعذر عرض هذه الصفحة' }) { Capture "failure-reference-$Volume.png"; throw "Reference $Volume download/render failed" }
    }
    throw "Reference $Volume did not render page $Page"
}
function ReadReference([int]$Volume,[int]$Page,[int]$Pages) {
    Top
    TapNode (Show "قراءة المرجع داخل التطبيق · صفحة $Page")
    Find 'قراءة المرجع داخل التطبيق' | Out-Null
    if (Lookup "فتح صفحة $Page") { Tap "فتح صفحة $Page" }
    else { Tap 'تنزيل المجلد وفتح الصفحة' }
    WaitReader $Volume $Page $Pages
    Find "صفحة الملف $Page / $Pages" | Out-Null
    Capture "reference-$Volume-page-$Page.png"
    Write-Output "PASS reference ${Volume}: verified download and rendered PDF page $Page of $Pages."
    Tap '→ العودة'
    Top
}
Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المقارنة'
Top
# Preserve the prior favourite state when rerunning this test.
$wasSaved=[bool](Lookup 'إزالة المسألة من المحفوظات')
if (-not $wasSaved) { TapNode (Show 'حفظ المسألة') }
Find 'إزالة المسألة من المحفوظات' | Out-Null
Tap 'محفوظاتي'
Find 'النية في الغسل من الجنابة' | Out-Null
Capture '01-saved-comparison.png'
Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'محفوظاتي'
Find 'النية في الغسل من الجنابة' | Out-Null
Tap 'فتح المقارنة'
TapNode (Show 'مشاركة المقارنة')
$activity=Device @('shell','dumpsys','activity','activities')
if (-not ($activity | Select-String 'topResumedActivity.*(ChooserActivity|ResolverActivity)')) { throw 'Android share chooser did not open' }
Device @('shell','input','keyevent','4') | Out-Null
Find 'المقارنة الفقهية' | Out-Null
Write-Output 'PASS favourites survive restart, saved comparison opens, and Android share chooser opens without sending.'
ReadReference 31 207 399
Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المقارنة'
TapNode (Show 'قراءة المرجع داخل التطبيق · صفحة 207')
Find 'المجلد محفوظ ومطابق للمصدر، وجاهز للقراءة دون اتصال.' | Out-Null
Tap 'فتح صفحة 207'
WaitReader 31 207 399
Capture '02-cached-reference-after-restart.png'
Tap '→ العودة'
Top
Write-Output 'PASS downloaded reference persists after restart and reopens from local storage without a new download.'
foreach ($case in @(@(43,360,450),@(27,274,406),@(23,232,383))) {
    if (Lookup 'إعادة الضبط') { Tap 'إعادة الضبط' }
    Search ([string]$case[1])
    Find 'النتائج: 1 من 15 مسألة' | Out-Null
    ReadReference $case[0] $case[1] $case[2]
}
Top
if (Lookup 'إعادة الضبط') { Tap 'إعادة الضبط' }
# Return to the first issue through the menu before restoring favourite state.
Tap 'الزكاة في مال الصغير والمجنون'
Tap 'النية في الغسل من الجنابة'
if (-not $wasSaved) { TapNode (Show 'إزالة المسألة من المحفوظات') }
Top
Find 'النتائج: 15 من 15 مسألة' | Out-Null
Write-Output 'PASS v0.7.0: favourites persistence, share chooser, four original reference volumes at exact cited pages, cached reopen, search and return navigation; no app data cleared.'
