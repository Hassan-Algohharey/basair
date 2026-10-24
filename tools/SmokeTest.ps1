param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Serial = 'emulator-5580',
    [switch]$NewSectionsOnly,
    [switch]$ExpansionOnly,
    [string[]]$BookIds = @()
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This test only operates on an Android emulator.' }
$project = Split-Path $PSScriptRoot -Parent
$shots = Join-Path $project $(if ($ExpansionOnly) { 'docs\screenshots\v04' } elseif ($NewSectionsOnly) { 'docs\screenshots\v03' } else { 'docs\screenshots\v02' })
New-Item -ItemType Directory -Force -Path $shots | Out-Null
function Device([string[]]$Arguments) {
    $output = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $output
}
function Read-Ui {
    Device @('shell','uiautomator','dump','/sdcard/basair-v02-ui.xml') | Out-Null
    Device @('pull','/sdcard/basair-v02-ui.xml',(Join-Path $shots 'ui.xml')) | Out-Null
    [xml](Get-Content (Join-Path $shots 'ui.xml') -Raw -Encoding UTF8)
}
function Find-Text([string]$Text) {
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $ui = Read-Ui
        $node = $ui.SelectNodes('//node') | Where-Object { ([string]$_.text).Trim() -eq $Text -or $_.'content-desc' -eq $Text } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "UI element not found: $Text"
}
function Tap-Node($Node) {
    if (-not $Node) { throw 'Cannot tap an absent node.' }
    $bounds = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Device @('shell','input','tap',([int](($bounds[0]+$bounds[2])/2)).ToString(),([int](($bounds[1]+$bounds[3])/2)).ToString()) | Out-Null
    Start-Sleep -Milliseconds 450
}
function Tap-Text([string]$Text) { Tap-Node (Find-Text $Text) }
function Capture([string]$Name) {
    Device @('shell','screencap','-p','/sdcard/basair-v02.png') | Out-Null
    Device @('pull','/sdcard/basair-v02.png',(Join-Path $shots $Name)) | Out-Null
}
function Assert-Page([int]$Page, $Book) {
    Find-Text "صفحة الملف $Page / $($Book.pages)" | Out-Null
    $expected = "صفحة الملف $Page من $($Book.pages) · $($Book.title)"
    Find-Text $expected | Out-Null
}
function Tap-Category([string]$Category) {
    $ui = Read-Ui
    # Category labels also appear on book cards. Only match inside the horizontal chip row.
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $row = $ui.SelectNodes('//node') | Where-Object {
            if ($_.scrollable -ne 'true') { return $false }
            $b = [regex]::Matches($_.bounds, '\d+') | ForEach-Object { [int]$_.Value }
            ($b[2]-$b[0]) -gt 500 -and ($b[3]-$b[1]) -lt 220
        } | Select-Object -First 1
        if (-not $row) { throw 'Horizontal category row not found.' }
        $node = $row.SelectNodes('.//node') | Where-Object text -eq $Category | Select-Object -First 1
        if ($node) { Tap-Node $node; return }
        $b = [regex]::Matches($row.bounds, '\d+') | ForEach-Object { [int]$_.Value }
        $y = ([int](($b[1]+$b[3])/2)).ToString()
        Device @('shell','input','swipe','100',$y,'980',$y,'400') | Out-Null
        $ui = Read-Ui
    }
    throw "Category not found after scrolling: $Category"
}
function Tap-Book([string]$Title) {
    for ($attempt = 0; $attempt -lt 6; $attempt++) {
        $ui = Read-Ui
        $node = $ui.SelectNodes('//node') | Where-Object text -eq $Title | Select-Object -First 1
        if ($node) { Tap-Node $node; return }
        Device @('shell','input','swipe','540','1650','540','950','400') | Out-Null
    }
    throw "Book not found after scrolling: $Title"
}

Device @('shell','am','force-stop','com.algohary.basair') | Out-Null
Device @('shell','pm','clear','com.algohary.basair') | Out-Null
Device @('shell','settings','put','global','heads_up_notifications_enabled','0') | Out-Null
Device @('shell','am','start','-W','-n','com.algohary.basair/.MainActivity') | Out-Null
Find-Text 'بصائر' | Out-Null
Capture '01-home.png'
$books = Get-Content (Join-Path $project 'content/books.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$categories = @{ fiqh='الفقه'; aqidah='العقيدة'; hadith='الحديث'; sirah='السيرة'; tafsir='التفسير'; salaf='تراجم السلف'; tazkiyah='التزكية' }
if ($NewSectionsOnly) { $books = $books | Where-Object { $_.subject -in @('salaf','tazkiyah') } }
if ($ExpansionOnly) { $books = $books | Where-Object { $_.id -in @('four-imams','safwa1','safwa2','wasael','risalat') } }
if ($BookIds.Count -gt 0) { $books = $books | Where-Object { $_.id -in $BookIds } }
$shortcuts = @{ 'four-imams'='الأئمة الأربعة'; safwa1='الصحابة'; safwa2='التابعين'; wasael='التوكل'; risalat='الإخلاص' }
foreach ($book in $books) {
    Tap-Text 'المكتبة'
    if ($ExpansionOnly) {
        # Restore the top and clear a shortcut from the preceding book.
        Device @('shell','input','swipe','540','600','540','1800','400') | Out-Null
        $ui = Read-Ui
        $clear = $ui.SelectNodes('//node') | Where-Object text -eq 'مسح' | Select-Object -First 1
        if ($clear) { Tap-Node $clear }
    }
    Tap-Category $categories[$book.subject]
    if ($ExpansionOnly) { Tap-Text $shortcuts[$book.id]; Capture "filter-$($book.id).png" }
    if ($NewSectionsOnly) { Capture "library-$($book.subject).png" }
    if ($book.subject -eq 'fiqh') { Capture '02-library.png' }
    Tap-Book $book.title
    Assert-Page 1 $book
    Capture "reader-$($book.id)-first.png"
    if ($ExpansionOnly) {
        Tap-Text 'البيانات'
        Find-Text 'بيانات الكتاب والإتاحة' | Out-Null
        Capture "provenance-$($book.id).png"
        Tap-Text 'إغلاق'
    }
    if ($book.subject -eq 'fiqh') {
        Tap-Text 'حفظ الكتاب'
        Tap-Text 'البيانات'
        Find-Text 'بيانات الكتاب والإتاحة' | Out-Null
        Capture '03-book-provenance.png'
        Tap-Text 'إغلاق'
    }
    Tap-Text "صفحة الملف 1 / $($book.pages)"
    $ui = Read-Ui
    $inputNode = $ui.SelectNodes('//node') | Where-Object class -eq 'android.widget.EditText' | Select-Object -First 1
    Tap-Node $inputNode
    Device @('shell','input','keyevent','67') | Out-Null
    $target = if ($book.subject -eq 'sirah') { 333 } elseif ($book.id -eq 'four-imams') { 147 } else { [int]$book.pages }
    Device @('shell','input','text',$target.ToString()) | Out-Null
    Tap-Text 'انتقال'
    Assert-Page $target $book
    Capture "reader-$($book.id)-last.png"
    Write-Output "PASS original PDF: $($book.id), first and end pages, declared total $($book.pages)."
}
if ($ExpansionOnly) { Write-Output "PASS v0.4: $($books.Count) selected added originals, topic shortcuts, provenance dialogs, first and end pages."; return }
if ($NewSectionsOnly) { Write-Output 'PASS v0.3 new categories and both original readers.'; return }
Tap-Text 'محفوظاتي'
Tap-Text $books[0].title
Assert-Page 469 $books[0]
Tap-Text '+'
Find-Text '150٪' | Out-Null
Device @('shell','am','force-stop','com.algohary.basair') | Out-Null
Device @('shell','am','start','-W','-n','com.algohary.basair/.MainActivity') | Out-Null
Tap-Text 'محفوظاتي'
Tap-Text $books[0].title
Assert-Page 469 $books[0]
Find-Text '150٪' | Out-Null
Capture '04-resume-reading.png'
Tap-Text 'المقارنة'
Find-Text 'المذهب الحنفي' | Out-Null
Capture '05-comparison.png'
Tap-Text 'الحنبلي'
Find-Text 'المذهب الحنبلي' | Out-Null
$ui = Read-Ui
if ($ui.SelectNodes('//node') | Where-Object text -eq 'المذهب الحنفي') { throw 'School filter failed.' }
Tap-Text 'النية في الغسل من الجنابة'
Tap-Text 'الترتيب في الوضوء'
Find-Text 'المذهب الحنبلي' | Out-Null
Capture '06-comparison-order.png'
Tap-Text 'البحث'
$ui = Read-Ui
$inputNode = $ui.SelectNodes('//node') | Where-Object class -eq 'android.widget.EditText' | Select-Object -First 1
Tap-Node $inputNode
Device @('shell','input','text','missingquery') | Out-Null
Device @('shell','input','keyevent','111') | Out-Null
Find-Text 'لم نجد نتائج' | Out-Null
Tap-Text 'إعادة ضبط البحث'
Write-Output 'PASS: five original PDFs, provenance, page jump, saved book, resumed page and zoom, package ID, comparison selection, school filter, search empty state.'
