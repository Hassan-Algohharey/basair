param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v081-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v081-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v081-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v081.png') | Out-Null
    Device @('pull','/sdcard/basair-v081.png',(Join-Path $shots $Name)) | Out-Null
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
if (Lookup 'Home') { Tap 'العربية' }
Tap 'المكتبة'
Tap 'الفقه'
$cases=@(
    @{school='الحنفي (1)';title='حاشية الطحطاوي على مراقي الفلاح';page=13;printed=13;total=490;source='المطبعة الميرية'},
    @{school='المالكي (1)';title='الرسالة لابن أبي زيد القيرواني';page=12;printed=12;total=191;source='المكتبة الثقافية'},
    @{school='الشافعي (1)';title='فتح القريب المجيب بشرح متن أبي شجاع';page=4;printed=3;total=71;source='المطبعة الخيرية'},
    @{school='الحنبلي (1)';title='عمدة الفقه';page=3;printed=3;total=160;source='معهد السنة'}
)
foreach ($case in $cases) {
    Top
    Tap $case.school
    TapNode (Show $case.title)
    WaitReader 'صفحة الملف ' | Out-Null
    Tap 'أبواب الكتاب'
    Tap "الطهارة · ص $($case.printed) · صفحة الملف $($case.page)"
    WaitReader "صفحة الملف $($case.page) من $($case.total)" | Out-Null
    Capture "$($case.total)-chapter.png"
    Tap 'البيانات'
    Find 'بيانات الكتاب والإتاحة' | Out-Null
    $details=(Ui).OuterXml
    if (-not $details.Contains($case.source)) { throw "Publisher missing: $($case.title)" }
    Capture "$($case.total)-source.png"
    Tap 'إغلاق'
    Tap "صفحة الملف $($case.page) / $($case.total)"
    $field=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.class -eq 'android.widget.EditText' } | Select-Object -First 1
    TapNode $field
    Device @('shell','input','keyevent','123') | Out-Null
    1..5 | ForEach-Object { Device @('shell','input','keyevent','67') | Out-Null }
    Device @('shell','input','text',$case.total.ToString()) | Out-Null
    Tap 'انتقال'
    WaitReader "صفحة الملف $($case.total) من $($case.total)" | Out-Null
    Capture "$($case.total)-last.png"
    Tap '→ العودة'
    Write-Output "PASS $($case.title): school filter, PDF chapter link, publisher and last page $($case.total)."
}
Restart
Tap 'English'
Tap 'Library'
Tap 'Fiqh'
Tap 'Shafii (1)'
TapNode (Show 'فتح القريب المجيب بشرح متن أبي شجاع')
WaitReader 'File page ' | Out-Null
Tap 'Book sections'
$nodes=(Ui).SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text.StartsWith('Zakat') }
if (-not $nodes) { throw 'English chapter label missing' }
TapNode ($nodes | Select-Object -First 1)
WaitReader 'File page 23 of 71' | Out-Null
Capture 'english-chapter.png'
Tap 'Back'
Restart
Tap 'العربية'
Find 'الرئيسية' | Out-Null
Write-Output 'PASS English chapter index, exact Shafii printed/file page offset and Arabic preference restored. No data cleared.'