$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'publishing/graphics'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$green = [System.Drawing.ColorTranslator]::FromHtml('#164E42')
$gold = [System.Drawing.ColorTranslator]::FromHtml('#E8D7AB')
function Draw-Book($Graphics, [float]$X, [float]$Y, [float]$Scale) {
    $state = $Graphics.Save()
    $Graphics.TranslateTransform($X,$Y)
    $Graphics.ScaleTransform($Scale,$Scale)
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.StartFigure()
    $path.AddBezier(24,30,35,27,45,29,54,35)
    $path.AddBezier(54,35,63,29,74,27,84,30)
    $path.AddLine(84,30,84,76)
    $path.AddBezier(84,76,73,73,63,75,54,81)
    $path.AddBezier(54,81,45,75,35,73,24,76)
    $path.CloseFigure()
    $brush = [System.Drawing.SolidBrush]::new($gold)
    $pen = [System.Drawing.Pen]::new($green,3)
    $Graphics.FillPath($brush,$path)
    $Graphics.DrawLine($pen,54,35,54,76)
    foreach ($line in @(@(31,42,46,44),@(62,44,77,42),@(31,53,46,55),@(62,55,77,53))) {
        $Graphics.DrawLine($pen,$line[0],$line[1],$line[2],$line[3])
    }
    $path.Dispose(); $brush.Dispose(); $pen.Dispose(); $Graphics.Restore($state)
}
$icon = [System.Drawing.Bitmap]::new(512,512)
$g = [System.Drawing.Graphics]::FromImage($icon)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear($green)
Draw-Book $g 0 0 (512/108)
$icon.Save((Join-Path $output 'icon-512.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $icon.Dispose()

$banner = [System.Drawing.Bitmap]::new(1024,500)
$g = [System.Drawing.Graphics]::FromImage($banner)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear($green)
Draw-Book $g 660 85 3
$brush = [System.Drawing.SolidBrush]::new($gold)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$ar = [System.Drawing.Font]::new('Tahoma',58,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
$en = [System.Drawing.Font]::new('Segoe UI',54,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
$small = [System.Drawing.Font]::new('Tahoma',25,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
$rtl = [System.Drawing.StringFormat]::new()
$rtl.FormatFlags = [System.Drawing.StringFormatFlags]::DirectionRightToLeft
$rtl.Alignment = [System.Drawing.StringAlignment]::Center
$ltr = [System.Drawing.StringFormat]::new()
$ltr.Alignment = [System.Drawing.StringAlignment]::Center
$g.DrawString('بصائر',$ar,$white,[System.Drawing.RectangleF]::new(65,110,585,100),$rtl)
$g.DrawString('Basair',$en,$brush,[System.Drawing.RectangleF]::new(65,207,585,90),$ltr)
$g.DrawString('مكتبة العلوم الإسلامية',$small,$white,[System.Drawing.RectangleF]::new(65,320,585,70),$rtl)
$banner.Save((Join-Path $output 'feature-1024x500.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$rtl.Dispose(); $ltr.Dispose(); $brush.Dispose(); $white.Dispose(); $ar.Dispose(); $en.Dispose(); $small.Dispose(); $g.Dispose(); $banner.Dispose()
Write-Output 'Created 512px store icon and 1024x500 feature graphic from the existing vector brand.'
