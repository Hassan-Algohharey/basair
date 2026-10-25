param(
 [Parameter(Mandatory=$true)][string]$Serial,
 [string]$Adb='C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe',
 [ValidateSet('All','Filters','ReaderAudio','LargeText')][string]$Phase='All'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v021-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v021-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v021-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v021.png') | Out-Null
    Device @('pull','/sdcard/basair-v021.png',(Join-Path $shots $Name)) | Out-Null
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





$oldServices=(Device @('shell','settings','get','secure','enabled_accessibility_services') | Out-String).Trim()
$oldEnabled=(Device @('shell','settings','get','secure','accessibility_enabled') | Out-String).Trim()
$talkback='com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService'
try {
 $existing=(Ui).SelectNodes('//node') | Where-Object {$_.package -eq $package -and $_.'content-desc'.StartsWith('صفحة الملف 14 من 18')} | Select-Object -First 1
 if (-not $existing) {
  ArabicHome; TapNode (Show 'بحث داخل متن التحفة'); InputText '42'; Tap 'قراءة الموضع · ص 35 · صفحة الملف 14'
  WaitReader 'صفحة الملف 14 من 18' | Out-Null
 }
 $services=if ($oldServices -and $oldServices -ne 'null') { (($oldServices.Split(':') + $talkback) | Select-Object -Unique) -join ':' } else {$talkback}
 Device @('shell','settings','put','secure','enabled_accessibility_services',$services) | Out-Null
 Device @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
 Start-Sleep -Seconds 2
 Device @('shell','am','start','-W','-n',"$package/com.algohary.basair.MainActivity") | Out-Null
 Capture 'talkback-ready.png'
 Device @('shell','input','tap','500','900') | Out-Null
 Capture 'talkback-touch-focus.png'
 Device @('shell','input','swipe','200','800','800','800','150') | Out-Null
 Start-Sleep -Seconds 3
 Capture 'talkback-swipe-focus.png'
 $enabled=(Device @('shell','settings','get','secure','accessibility_enabled') | Out-String).Trim()
 if ($enabled -ne '1') {throw 'Accessibility service not enabled during capture'}
 Write-Output 'CAPTURED with TalkBack enabled: actual reader touch/swipe screenshots. Manual visual review required; UIAutomator may omit accessibility overlays. No phone permissions granted or spoken-output certification.'
} finally {
 if ($oldServices -and $oldServices -ne 'null') {Device @('shell','settings','put','secure','enabled_accessibility_services',$oldServices) | Out-Null}
 else {Device @('shell','settings','delete','secure','enabled_accessibility_services') | Out-Null}
 if ($oldEnabled -and $oldEnabled -ne 'null') {Device @('shell','settings','put','secure','accessibility_enabled',$oldEnabled) | Out-Null}
 else {Device @('shell','settings','delete','secure','accessibility_enabled') | Out-Null}
}
