param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v08-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $result=& $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v08-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v08-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    $from=if ($Up) { .76 } else { .30 }; $to=if ($Up) { .30 } else { .76 }
    Device @('shell','input','swipe',([int]($width*.5)).ToString(),([int]($height*$from)).ToString(),([int]($width*.5)).ToString(),([int]($height*$to)).ToString(),'250') | Out-Null
}
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 9;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v08.png') | Out-Null
    Device @('pull','/sdcard/basair-v08.png',(Join-Path $shots $Name)) | Out-Null
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

Restart
if (Lookup 'الرئيسية') { Tap 'English' }
Find 'Home' | Out-Null
Capture '01-home-en.png'
Tap 'Library'
Tap 'Fiqh'
foreach ($school in @('Hanafi (0)','Maliki (0)','Shafii (0)','Hanbali (0)')) { Find $school | Out-Null }
Capture '02-schools-en.png'
Tap 'Hanafi (0)'
Show 'No matching school books yet' | Out-Null
Capture '02b-school-empty-en.png'
Top
Tap 'Shared references'
Search 'zakat'
TapNode (Show 'Purification')
TapNode (Show 'الفقه الميسر في ضوء الكتاب والسنة')
$reader=WaitReader 'File page '
$pageBefore=$reader.'content-desc'
$addedBookmark=-not [bool](Lookup 'Remove page bookmark')
if ($addedBookmark) { Tap 'Bookmark this page' }
$addedSave=-not [bool](Lookup 'Remove saved book')
if ($addedSave) { Tap 'Save book' }
Find 'Remove saved book' | Out-Null
Capture '03-reader-en.png'
Tap 'Back'
Top
$field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
if ($field.text -ne 'zakat') { throw 'Query lost on return from reader' }
$topicNode=Show 'Purification'
while ($topicNode.checked -ne 'true' -and $topicNode.ParentNode.Name -eq 'node') { $topicNode=$topicNode.ParentNode }
if ($topicNode.checked -ne 'true') { throw 'Topic filter lost on return from reader' }
Write-Output 'PASS English UI, four school sections, honest empty school, shared book, English metadata search and return state.'

Restart
Find 'Home' | Out-Null
Tap 'Saved'
Tap 'الفقه الميسر في ضوء الكتاب والسنة'
$reader=WaitReader 'File page '
if ($reader.'content-desc' -ne $pageBefore) { throw 'Reading position lost after restart' }
Find 'Remove page bookmark' | Out-Null
Tap 'Back'
Tap 'Home'
Top
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Capture '04-home-ar.png'
Tap 'محفوظاتي'
Tap 'الفقه الميسر في ضوء الكتاب والسنة'
WaitReader 'صفحة الملف ' | Out-Null
Find 'إزالة علامة الصفحة' | Out-Null
if ($addedBookmark) { Tap 'إزالة علامة الصفحة' }
if ($addedSave) { Tap 'إزالة الكتاب من المحفوظات' }
Tap 'البيانات'
Find 'بيانات الكتاب والإتاحة' | Out-Null
Capture '05-book-details-ar.png'
Tap 'إغلاق'
Tap '→ العودة'
Write-Output 'PASS saved book, bookmark and reading position persist across process restart and language switch; test additions restored.'

Tap 'المكتبة'
Top
Tap 'الفقه'
foreach ($school in @('الحنفي (0)','المالكي (0)','الشافعي (0)','الحنبلي (0)')) { Find $school | Out-Null }
Capture '06-schools-ar.png'
TapNode (Show 'المراجع المشتركة')
Top
if (Lookup 'مسح') { Tap 'مسح' }
Top
Tap 'المراجع المشتركة'
$volumeNode=$null
for ($i=0;$i -lt 9;$i++) {
    $volumeNode=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -match 'الموسوعة الفقهية الكويتية · ج (31|٣١) · الصفحات: (399|٣٩٩)' } | Select-Object -First 1
    if ($volumeNode) { break }; Swipe $true
}
if (-not $volumeNode) { throw 'Shared reference volume 31 missing' }
TapNode $volumeNode
Find 'المجلد محفوظ ومطابق للمصدر، وجاهز للقراءة دون اتصال.' | Out-Null
$openNode=Lookup 'فتح صفحة ١'
if (-not $openNode) { $openNode=Find 'فتح صفحة 1' }
TapNode $openNode
WaitReader 'صفحة الملف ' | Out-Null
Capture '07-cached-reference-ar.png'
Tap '→ العودة'
Restart
Find 'الرئيسية' | Out-Null
Write-Output 'PASS Arabic RTL, cached reference preserved from 0.7.0 and rendered locally, Arabic preference persists after restart.'

Tap 'English'
Find 'Home' | Out-Null
Tap 'Search'
Search 'companions'
Show 'صفة الصفوة — الجزء الأول: الصحابة' | Out-Null
Capture '08-global-search-en.png'
Tap 'Compare'
Find 'Fiqh comparison' | Out-Null
TapNode (Show 'Read reference in app · Page 207')
Find 'The volume matches the source and is ready to read offline.' | Out-Null
Tap 'Open page 207'
$referencePage=WaitReader 'File page 207 of 399'
Capture '09-reference-207-en.png'
Tap 'Back'
Tap 'Home'
Top
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Write-Output 'PASS English global search and comparison controls, exact citation page 207 rendered, final UI restored to Arabic.'
