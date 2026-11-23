# Original 256 x 256 alpha atlas for the Uruk-hai adrenaline edge effect.
# White vessel pixels are tinted and faded by the renderer; the center remains transparent.
Add-Type -AssemblyName System.Drawing

function Draw-Vessel {
    param(
        [System.Drawing.Graphics] $Graphics,
        [int[][]] $Points,
        [int] $Width,
        [int] $Alpha
    )
    $drawingPoints = New-Object 'System.Drawing.Point[]' $Points.Count
    for ($index = 0; $index -lt $Points.Count; $index++) {
        $drawingPoints[$index] = [System.Drawing.Point]::new($Points[$index][0], $Points[$index][1])
    }
    $halo = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb([Math]::Max(12, [int]($Alpha / 5)), 255, 255, 255), $Width + 2)
    $line = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb($Alpha, 255, 255, 255), $Width)
    try {
        $Graphics.DrawLines($halo, $drawingPoints)
        $Graphics.DrawLines($line, $drawingPoints)
    } finally {
        $halo.Dispose()
        $line.Dispose()
    }
}

$image = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($image)
try {
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver

    # Left edge: two trunks with irregular capillary forks.
    Draw-Vessel $graphics @(@(0,42),@(9,48),@(13,59),@(22,68),@(25,83),@(35,94),@(39,111)) 2 220
    Draw-Vessel $graphics @(@(14,60),@(7,68),@(3,78),@(0,82)) 1 170
    Draw-Vessel $graphics @(@(24,82),@(17,91),@(15,103),@(8,112)) 1 155
    Draw-Vessel $graphics @(@(34,94),@(29,105),@(31,117),@(25,126)) 1 135
    Draw-Vessel $graphics @(@(0,154),@(8,148),@(16,150),@(23,141),@(31,139),@(40,129)) 2 205
    Draw-Vessel $graphics @(@(15,150),@(19,160),@(28,168),@(30,181)) 1 150
    Draw-Vessel $graphics @(@(23,141),@(18,132),@(10,127),@(5,117),@(0,115)) 1 165
    Draw-Vessel $graphics @(@(29,168),@(23,177),@(20,188),@(12,196),@(10,207)) 1 120

    # Right edge: deliberately different branching to avoid a mirrored vignette.
    Draw-Vessel $graphics @(@(255,31),@(247,38),@(244,50),@(235,58),@(232,73),@(222,84),@(219,101)) 2 215
    Draw-Vessel $graphics @(@(244,50),@(251,59),@(255,70)) 1 150
    Draw-Vessel $graphics @(@(232,73),@(240,82),@(243,94),@(251,101),@(255,113)) 1 170
    Draw-Vessel $graphics @(@(222,84),@(226,95),@(224,109),@(228,119)) 1 125
    Draw-Vessel $graphics @(@(255,178),@(247,171),@(239,172),@(232,163),@(224,158),@(216,145)) 2 210
    Draw-Vessel $graphics @(@(239,172),@(237,185),@(229,194),@(228,207)) 1 150
    Draw-Vessel $graphics @(@(232,163),@(240,153),@(246,143),@(255,139)) 1 165
    Draw-Vessel $graphics @(@(224,158),@(226,147),@(222,136),@(224,125)) 1 120

    # Top edge: short roots leave the crosshair and sight line clear.
    Draw-Vessel $graphics @(@(49,0),@(55,8),@(67,11),@(74,20),@(88,23),@(96,34)) 2 190
    Draw-Vessel $graphics @(@(67,11),@(72,4),@(80,0)) 1 135
    Draw-Vessel $graphics @(@(87,23),@(82,31),@(83,40)) 1 120
    Draw-Vessel $graphics @(@(170,0),@(166,9),@(156,14),@(151,25),@(139,30),@(134,39)) 2 185
    Draw-Vessel $graphics @(@(156,14),@(151,6),@(143,0)) 1 135
    Draw-Vessel $graphics @(@(151,25),@(160,30),@(165,38)) 1 115

    # Bottom edge: asymmetric forks frame the hotbar without covering its center.
    Draw-Vessel $graphics @(@(35,255),@(41,246),@(52,242),@(58,231),@(72,228),@(80,218)) 2 195
    Draw-Vessel $graphics @(@(52,242),@(57,250),@(67,255)) 1 130
    Draw-Vessel $graphics @(@(72,228),@(67,219),@(68,211)) 1 110
    Draw-Vessel $graphics @(@(207,255),@(202,246),@(190,241),@(184,231),@(171,226),@(164,216)) 2 200
    Draw-Vessel $graphics @(@(190,241),@(184,249),@(176,255)) 1 135
    Draw-Vessel $graphics @(@(171,226),@(176,217),@(174,208)) 1 115

    $destination = Join-Path $PSScriptRoot '../../src/main/resources/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels.png'
    $directory = Split-Path $destination -Parent
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $image.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)

    # Nine full-stroke frames, in a power-of-two 4 x 4 sheet. Coverage advances
    # from the actual stroke into its immediate neighbors, reaching the same
    # material alpha at the peak. Existing core pixels (alpha >= 100) stay fixed.
    # This replaces the faint outer-halo overlay; the renderer draws ONE frame.
    $swell = [System.Drawing.Bitmap]::new(1024, 1024, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt 256; $y++) {
            for ($x = 0; $x -lt 256; $x++) {
                $original = $image.GetPixel($x, $y)
                $neighborAlpha = 0
                for ($offsetY = -1; $offsetY -le 1; $offsetY++) {
                    for ($offsetX = -1; $offsetX -le 1; $offsetX++) {
                        $nx = $x + $offsetX
                        $ny = $y + $offsetY
                        if ($nx -ge 0 -and $nx -lt 256 -and $ny -ge 0 -and $ny -lt 256) {
                            $candidate = [int]$image.GetPixel($nx, $ny).A
                            if ($candidate -ge 100) {
                                $neighborAlpha = [Math]::Max($neighborAlpha, $candidate)
                            }
                        }
                    }
                }
                for ($frame = 0; $frame -lt 9; $frame++) {
                    $color = $original
                    $inCenter = $x -ge 48 -and $x -lt 208 -and $y -ge 48 -and $y -lt 208
                    if ($frame -gt 0 -and $original.A -lt 100 -and -not $inCenter) {
                        $alpha = [Math]::Max([int]$original.A, [int][Math]::Round($neighborAlpha * $frame / 8.0))
                        $color = [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255)
                    }
                    $swell.SetPixel($x + ($frame % 4) * 256, $y + [Math]::Floor($frame / 4) * 256, $color)
                }
            }
        }
        $swell.Save((Join-Path $directory 'uruk_rage_vessels_swell.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $swell.Dispose()
    }
} finally {
    $graphics.Dispose()
    $image.Dispose()
}
