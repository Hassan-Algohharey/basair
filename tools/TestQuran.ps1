param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v09-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v09-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v09-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v09.png') | Out-Null
    Device @('pull','/sdcard/basair-v09.png',(Join-Path $shots $Name)) | Out-Null
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

function QTop { 1..10 | ForEach-Object { Swipe $false } }
function SetQField([string]$Label,[string]$Value) {
 $node=Show $Label
 while ($node.class -ne 'android.widget.EditText' -and $node.ParentNode.Name -eq 'node') { $node=$node.ParentNode }
 if ($node.class -ne 'android.widget.EditText') { throw 'Missing Quran number field' }
 TapNode $node
 Device @('shell','input','keyevent','123') | Out-Null
 1..5 | ForEach-Object { Device @('shell','input','keyevent','67') | Out-Null }
 Device @('shell','input','text',$Value) | Out-Null
 Device @('shell','input','keyevent','4') | Out-Null
}
function QJump([int]$Page) {
 QTop; Tap 'انتقال'; SetQField 'رقم الصفحة (1–604)' $Page.ToString(); Tap 'الانتقال إلى الصفحة'
 for ($i=0;$i -lt 10;$i++) { if (Contains "الصفحة $Page من 604") { return }; Start-Sleep -Seconds 1 }
 throw "Quran page did not open: $Page"
}
function OpenQ { Restart; if (Lookup 'Home') { Tap 'العربية' }; Tap 'القرآن الكريم والتفسير الميسر'; Find 'القرآن الكريم والتفسير' | Out-Null }
$payload=Get-Content (Join-Path $project 'content/quran/ede83bce2ddc925c/604.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$lastVerse=($payload.ayahs | Where-Object { $_.surah -eq 114 -and $_.ayah -eq 6 }).text
OpenQ
Capture '01-index-ar.png'
Tap 'القراءة'
Find 'تفسير الآية' | Out-Null
Capture '02-fatiha-tafsir.png'
QTop
if (Lookup 'حفظ علامة الصفحة') { Tap 'حفظ علامة الصفحة' }
Tap 'انتقال'
SetQField 'رقم السورة (1–114)' '114'
SetQField 'رقم الآية في السورة' '6'
Tap 'الانتقال إلى الآية'
Find $lastVerse | Out-Null
Capture '03-last-ayah-focused.png'
QTop
Find 'الصفحة 604 من 604 · الجزء 30' | Out-Null
if (Lookup 'حفظ علامة الصفحة') { Tap 'حفظ علامة الصفحة' }
QJump 121
Find 'تفسير الآية' | Out-Null
Capture '04-page121.png'
QTop; Tap 'انتقال'; SetQField 'رقم الجزء (1–30)' '30'; Tap 'الانتقال إلى الجزء'
Find 'الصفحة 582 من 604 · الجزء 30' | Out-Null
Find 'تفسير الآية' | Out-Null
QTop; Tap 'التنزيلات'
Toggle 'قراءة المحفوظ فقط دون اتصال' $true
Tap 'إغلاق'
QJump 300
Find 'هذه الصفحة غير محفوظة. أوقف «قراءة المحفوظ فقط» ثم أعد المحاولة لتنزيلها.' | Out-Null
Capture '05-offline-missing.png'
QJump 604
Find 'تفسير الآية' | Out-Null
QTop; Tap 'التنزيلات'
TapNode (Show 'حذف التنزيلات')
TapNode (Show 'حذف التنزيلات')
Find 'الصفحات المحفوظة: 0' | Out-Null
Tap 'إغلاق'
OpenQ
Tap 'العلامات'
$index=Get-Content (Join-Path $project 'app/src/main/assets/quran-index.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Show ('الصفحة 604 · '+$index.surahs[111].name+' · 1') | Out-Null
Tap 'إغلاق'
Tap 'التنزيلات'
Toggle 'قراءة المحفوظ فقط دون اتصال' $false
TapNode (Show 'تنزيل القرآن والتفسير كاملين')
TapNode (Show 'بدء التنزيل')
$complete=$false
for ($i=0;$i -lt 20;$i++) { if (Lookup 'الصفحات المحفوظة: 604') { $complete=$true;break }; Start-Sleep -Seconds 1 }
if (-not $complete) { throw 'Complete Quran archive did not verify and expand' }
Capture '06-all604-downloaded.png'
Toggle 'قراءة المحفوظ فقط دون اتصال' $true
Tap 'إغلاق'
Tap 'القراءة'
QJump 300
Find 'تفسير الآية' | Out-Null
Capture '07-offline-page300.png'
OpenQ
Find 'متابعة من الصفحة 300' | Out-Null
Tap 'التنزيلات'
Toggle 'قراءة المحفوظ فقط دون اتصال' $false
Tap 'إغلاق'
Restart
Tap 'English'
Tap 'Quran and Al-Tafsir Al-Muyassar'
Find 'Quran and tafsir' | Out-Null
Capture '08-index-en.png'
Tap 'Read'
Find 'Verse tafsir' | Out-Null
Capture '09-reader-en.png'
Top; Tap 'Index'
Search '114'
$index=Get-Content (Join-Path $project 'app/src/main/assets/quran-index.json') -Raw -Encoding UTF8 | ConvertFrom-Json
TapNode (Show ('114. '+$index.surahs[113].englishName))
Find 'Verse tafsir' | Out-Null
Capture '10-surah-filter-en.png'
Restart; Tap 'العربية'
Tap 'المكتبة'
Search 'Quran'
Find 'القرآن الكريم والتفسير الميسر' | Out-Null
Capture '11-library-quran-search.png'
Tap 'القرآن الكريم والتفسير الميسر'
Find 'القرآن الكريم والتفسير' | Out-Null
Tap 'المصادر والإتاحة'
Show 'المصدر الرسمي: مجمع الملك فهد' | Out-Null
Show 'مصدر فهرسة الصفحات: Tanzil' | Out-Null
Capture '12-source-attribution.png'
Tap 'إغلاق'
Restart
Find 'الرئيسية' | Out-Null
Write-Output 'PASS Quran fonts and tafsir, last ayah focus, page and juz navigation, bookmarks after cache deletion/restart, cached-only restriction, complete 604-page archive, offline page 300, saved position and English UI; restored Arabic.'
