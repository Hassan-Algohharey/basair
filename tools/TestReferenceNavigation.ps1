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


Device @('shell','am','force-stop',$package) | Out-Null
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Tap 'المقارنة'
Search '274'
Tap 'الحنفي'
Find 'النتائج: 1 من 15 مسألة' | Out-Null
TapNode (Show 'قراءة المرجع داخل التطبيق · صفحة 274')
Find 'المجلد محفوظ ومطابق للمصدر، وجاهز للقراءة دون اتصال.' | Out-Null
Tap 'فتح صفحة 274'
Find 'صفحة الملف 274 / 406' | Out-Null
Tap '→ العودة'
Top
Find 'النتائج: 1 من 15 مسألة' | Out-Null
$chip=Find 'الحنفي'
while ($chip.clickable -ne 'true' -and $chip.ParentNode.Name -eq 'node') { $chip=$chip.ParentNode }
if ($chip.checked -ne 'true' -and $chip.selected -ne 'true') { throw 'School filter was lost on return from reader' }
Capture '03-filters-retained-after-reader.png'
Write-Output 'PASS final build: query and selected school survive reference reader navigation; cached page 274 opens.'
Tap 'البحث'
Search '207'
TapNode (Show 'النية في الغسل من الجنابة')
Top
Find 'النتائج: 15 من 15 مسألة' | Out-Null
Find 'النية في الغسل من الجنابة' | Out-Null
Capture '04-global-search-opens-selected-issue.png'
Write-Output 'PASS final build: global search opens its requested comparison and clears incompatible prior comparison filters.'
