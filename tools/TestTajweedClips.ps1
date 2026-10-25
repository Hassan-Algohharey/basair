param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project ('publishing/screenshots/v0151-' + $(if ($Serial.StartsWith('emulator-')) { 'emulator' } else { 'phone' }))
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v0151-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v0151-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v0151.png') | Out-Null
    Device @('pull','/sdcard/basair-v0151.png',(Join-Path $shots $Name)) | Out-Null
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
    Show 'تكرار المثال فقط' | Out-Null
    (Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.CheckBox' } | Select-Object -First 1
}
function ClockValues {
    $line=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.StartsWith('موضع الاستماع:') } | Select-Object -First 1
    if (-not $line) { throw 'Clip clock not visible' }
    $values=[regex]::Matches($line.text,'(\d+):(\d+):(\d+)')
    if ($values.Count -ne 2) { throw 'Clip duration missing' }
    @($values | ForEach-Object { [int64]$_.Groups[1].Value*3600+[int64]$_.Groups[2].Value*60+[int64]$_.Groups[3].Value })
}
$cases=@(@('meem',7860),@('stressed',6620),@('natural',5310),@('articulation',5310),@('without',4400),@('solar',5310))
$results=@()
foreach ($case in $cases) {
    Restart
    if (Lookup 'Home') { Tap 'العربية' }
    TapNode (Show 'التجويد')
    Search $case[0]
    Show 'أمثلة حفص القصيرة: 1' | Out-Null
    TapNode (Show 'استمع للمثال')
    $box=RepeatBox
    if ($box.checked -eq 'true') { TapNode $box }
    $clocks=ClockValues
    if ($clocks[1] -ne [math]::Floor($case[1]/1000)) { throw "Wrong duration for $($case[0]): $clocks" }
    TapNode (Show 'تشغيل')
    Show 'إيقاف مؤقت' | Out-Null
    Start-Sleep -Seconds ([int][math]::Ceiling($case[1]/1000)+1)
    Show 'تشغيل' | Out-Null
    $ended=ClockValues
    if ($ended[0] -lt $ended[1]-1 -or $ended[0] -gt $ended[1] -or $ended[1] -gt 8) { throw "Playback escaped clip: $ended" }
    Capture ("clip-"+$case[0]+'.png')
    TapNode (Show 'إيقاف المشغل')
    $results+="$($case[0]): $($ended[0])/$($ended[1])s"
    Write-Output "PASS bounded stop: $($results[-1])"
}
Restart
TapNode (Show 'التجويد')
Search 'stressed'
TapNode (Show 'استمع للمثال')
$box=RepeatBox
if ($box.checked -ne 'true') { TapNode $box }
TapNode (Show 'تشغيل')
Show 'إيقاف مؤقت' | Out-Null
Device @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Seconds 14
Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
Show 'إيقاف مؤقت' | Out-Null
$clock=ClockValues
if ($clock[1] -ne 6 -or $clock[0] -gt 6) { throw "Background repeat escaped clip: $clock" }
Tap 'إيقاف مؤقت'
$paused=AudioPosition
Capture '07-background-clip-loop.png'
Restart
TapNode (Show 'التجويد')
TapNode (Show 'متابعة آخر درس اخترته: الغنة: إِنَّا')
$box=RepeatBox
if ($box.checked -ne 'true') { throw 'Clip repeat not restored' }
$restored=AudioPosition
if ([math]::Abs($restored-$paused) -gt 1000) { throw "Clip position not restored: $paused -> $restored" }
Show 'نسخة محفوظة متاحة دون اتصال' | Out-Null
$wifiState=(Device @('shell','settings','get','global','wifi_on') | Out-String).Trim()
$dataState=(Device @('shell','settings','get','global','mobile_data') | Out-String).Trim()
try {
    Device @('shell','svc','wifi','disable') | Out-Null
    Device @('shell','svc','data','disable') | Out-Null
    TapNode (Show 'تشغيل')
    Show 'إيقاف مؤقت' | Out-Null
    Start-Sleep -Seconds 10
    Show 'إيقاف مؤقت' | Out-Null
    $offline=ClockValues
    if ($offline[1] -ne 6 -or $offline[0] -gt 6) { throw 'Offline playback escaped clip' }
    Capture '08-offline-clip-loop.png'
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
Capture '09-english-clip-range.png'
TapNode (Show 'Listen to the example')
Show 'Repeat only the example' | Out-Null
Capture '10-english-clip-player.png'
Restart
Tap 'العربية'
TapNode (Show 'التجويد')
Write-Output "PASS: all 6 clips stop at their own ends; background/offline clip repeat; position $paused -> $restored ms; Arabic/English."
