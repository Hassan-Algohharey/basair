param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'C:/Users/hassa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
)
$ErrorActionPreference='Stop'
$package='com.algohary.basair.preview'
$project=Split-Path $PSScriptRoot -Parent
$shots=Join-Path $project 'publishing/screenshots/v083-phone'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $result=& $Adb -s $Serial @Arguments 2>$null
    $ErrorActionPreference=$previousPreference
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
function Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v083-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v083-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
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
    Device @('shell','screencap','-p','/sdcard/basair-v083.png') | Out-Null
    Device @('pull','/sdcard/basair-v083.png',(Join-Path $shots $Name)) | Out-Null
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
Tap 'المكتبة'
Tap 'الحديث'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
$previous=WaitReader 'صفحة الملف '
$oldPage=[int]([regex]::Match($previous.'content-desc','صفحة الملف (\d+)').Groups[1].Value)
Jump 17
WaitReader 'صفحة الملف 17 من 61' | Out-Null
ToolbarTap 'ملاحظاتي'
Tap 'كتابة أو تعديل ملاحظة الصفحة 17'
$field=(Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.package -eq $package } | Select-Object -First 1
if ($field.text -ne '') { throw 'Test page already has a note; preserve it and choose an unused page.' }
TapNode $field
Device @('shell','input','text','Basair083Note') | Out-Null
Device @('shell','input','keyevent','4') | Out-Null
Tap 'حفظ الملاحظة'
Find 'Basair083Note' | Out-Null
Capture '01-note-ar.png'
Restart
Tap 'ملاحظاتي'
Find 'Basair083Note' | Out-Null
Search 'Basair083Note'
Find 'Basair083Note' | Out-Null
Tap 'فتح الصفحة'
WaitReader 'صفحة الملف 17 من 61' | Out-Null
ToolbarTap 'ملاحظاتي'
Tap 'تعديل'
$field=(Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.package -eq $package } | Select-Object -First 1
TapNode $field
Device @('shell','input','keyevent','123') | Out-Null
Device @('shell','input','text','Edited') | Out-Null
Device @('shell','input','keyevent','4') | Out-Null
Tap 'حفظ الملاحظة'
Find 'Basair083NoteEdited' | Out-Null
Tap 'إغلاق'
ToolbarTap 'التنزيل والتخزين'
TapNode (Show 'حذف ملفات هذا الكتاب')


Top
if (-not (Contains 'المحفوظ: 0 من 61')) { throw 'Book download was not deleted' }
Capture '02-download-deleted.png'
Tap 'إغلاق'
Restart
Tap 'ملاحظاتي'
Find 'Basair083NoteEdited' | Out-Null
Write-Output 'PASS note saved, searched, edited and kept after app restart and book download deletion.'
Tap 'تعديل'
$field=(Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.package -eq $package } | Select-Object -First 1
TapNode $field
Device @('shell','input','keyevent','123') | Out-Null
Device @('shell','input','text','Unsaved') | Out-Null
Device @('shell','input','keyevent','4') | Out-Null
Tap 'إلغاء'
Find 'تجاهل التعديلات غير المحفوظة؟' | Out-Null
Tap 'إلغاء'
Find 'Basair083NoteEditedUnsaved' | Out-Null
Tap 'إلغاء'
Tap 'تجاهل التعديلات'
Find 'Basair083NoteEdited' | Out-Null
Tap 'تعديل'
Tap 'حذف الملاحظة'
Find 'حذف هذه الملاحظة نهائيًا؟' | Out-Null
Tap 'إلغاء'
Tap 'إلغاء'
Find 'Basair083NoteEdited' | Out-Null
Restart
Tap 'English'
Tap 'My notes'
Find 'Basair083NoteEdited' | Out-Null
Capture '03-notes-en.png'
Tap 'Edit'
Tap 'Delete note'
Find 'Permanently delete this note?' | Out-Null
Tap 'Delete note'
if (Lookup 'Basair083NoteEdited') { throw 'Deleted note is still visible' }
Restart
Tap 'My notes'
if (Lookup 'Basair083NoteEdited') { throw 'Deleted note returned after restart' }
Capture '04-empty-notes-en.png'
Restart
Tap 'العربية'
Tap 'المكتبة'
Tap 'الحديث'
TapNode (Show 'الأربعون النووية مع زيادة ابن رجب')
Jump $oldPage
WaitReader 'صفحة الملف ' | Out-Null
Tap '→ العودة'
Restart
Write-Output 'PASS discard confirmation and keep editing, cancelled deletion, English notes, confirmed deletion persisted, requested page restored and Arabic Home.'
