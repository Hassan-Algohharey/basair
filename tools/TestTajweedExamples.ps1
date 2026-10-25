param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project ('publishing/screenshots/v015-' + $(if ($Serial.StartsWith('emulator-')) { 'emulator' } else { 'phone' }))
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v015-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v015-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
function Top { 1..4 | ForEach-Object { Swipe $false } }
function Show([string]$Text) {
    for ($i=0;$i -lt 20;$i++) { $node=Lookup $Text; if ($node) { return $node }; Swipe $true }
    throw "Not found after scroll: $Text"
}
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v015.png') | Out-Null
    Device @('pull','/sdcard/basair-v015.png',(Join-Path $shots $Name)) | Out-Null
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

function AudioPosition {
    $line=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.StartsWith('موضع الاستماع:') } | Select-Object -First 1
    if (-not $line) { throw 'Listening position not visible' }
    $clock=[regex]::Match($line.text,'(\d+):(\d+):(\d+)')
    return ([int64]$clock.Groups[1].Value*3600+[int64]$clock.Groups[2].Value*60+[int64]$clock.Groups[3].Value)*1000
}
function WaitVerse([int]$Number) {
    for ($i=0;$i -lt 18;$i++) {
        $node=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -match "الآية $Number`$" } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "Verse $Number did not open"
}
function RepeatBox {
    Show 'تكرار التسجيل كاملًا' | Out-Null
    (Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.CheckBox' } | Select-Object -First 1
}
Restart
if (Lookup 'Home') { Tap 'العربية' }
TapNode (Show 'التجويد')
Search 'meem'
Show 'أمثلة حفص القصيرة: 1' | Out-Null
Capture '01-hafs-example.png'
TapNode (Show 'قراءة تحفة الأطفال والغلمان في تجويد القرآن — صفحة الملف 8، ص 29')
WaitReader 'صفحة الملف 8 ' | Out-Null
Capture '02-meem-book-source.png'
Tap '→ العودة'
TapNode (Show 'آية المثال وتفسيرها: 105:4')
WaitVerse 4 | Out-Null
Capture '03-meem-verse.png'
Tap '→ العودة'
Top
Tap 'مسح'
Search 'ghunnah'
TapNode (Show 'استمع للمثال ضمن السورة')
Show 'المواد الصوتية المتاحة: 1' | Out-Null
$box=RepeatBox
if (-not $box) { throw 'Repeat checkbox missing' }
if ($box.checked -ne 'true') { TapNode $box }
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
$before=AudioPosition
Device @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Seconds 5
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Show 'إيقاف مؤقت' | Out-Null
$after=AudioPosition
if ($after -le $before) { throw 'Hafs playback did not advance in background' }
$nearEnd=AudioPosition
$looped=$nearEnd
for ($i=0;$i -lt 12;$i++) {
    Start-Sleep -Seconds 1
    $current=AudioPosition
    if ($current -lt $nearEnd-5000) { $looped=$current; break }
    $nearEnd=$current
}
if ($looped -ge $nearEnd) { throw "Repeat did not loop: $nearEnd -> $looped" }
Tap 'إيقاف مؤقت'
$paused=AudioPosition
Capture '04-repeat-player.png'
Restart
TapNode (Show 'التجويد')
TapNode (Show 'متابعة آخر درس اخترته: الغنة: إِنَّا')
$box=RepeatBox
if ($box.checked -ne 'true') { throw 'Repeat choice not restored' }
$restored=AudioPosition
if ([math]::Abs($paused-$restored) -gt 2500) { throw "Position not restored: $paused -> $restored" }
Capture '05-restored-repeat-position.png'
TapNode (Show 'تنزيل للاستماع دون اتصال')
Show 'نسخة محفوظة متاحة دون اتصال' | Out-Null
$wifiState=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
$dataState=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
try {
    Device @('shell','svc','wifi','disable') | Out-Null
    Device @('shell','svc','data','disable') | Out-Null
    Top
    TapNode (Show 'تشغيل')
    Show 'إيقاف مؤقت' | Out-Null
    $offlineBefore=AudioPosition
    Start-Sleep -Seconds 3
    $offlineAfter=AudioPosition
    if ($offlineAfter -eq $offlineBefore) { throw 'New downloaded surah did not play offline' }
    Capture '06-offline-surah.png'
    Tap 'إيقاف مؤقت'
    TapNode (Show 'إيقاف المشغل')
} finally {
    if ($wifiState -eq '1') { Device @('shell','svc','wifi','enable') | Out-Null }
    if ($dataState -eq '1') { Device @('shell','svc','data','enable') | Out-Null }
}
Restart
Tap 'English'
TapNode (Show 'Tajweed')
Search 'meem'
Show 'Short Hafs examples: 1' | Out-Null
Capture '07-english-example.png'
TapNode (Show 'Listen to the example within the surah')
Show 'Repeat the entire recording' | Out-Null
Show 'Source and rights' | Out-Null
Capture '08-english-source.png'
Restart
Tap 'العربية'
TapNode (Show 'التجويد')
Write-Output "PASS: Arabic/English; book/verse links; background $before -> $after; repeat $nearEnd -> $looped; restored $paused -> $restored; offline $offlineBefore -> $offlineAfter ms."
