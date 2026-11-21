# Original 7 x 9 pixel redraw of the Angerthas Moria cirth used by the Dwarf HUD.
# Order: E46 N22 D9 U42 R12 A48 S54 F3 T8 I39 G19.
# '#' is an opaque rune pixel; '.' is transparent. No font ships with the mod.
Add-Type -AssemblyName System.Drawing
$glyphs = @(
    @('#.....#','#.....#','#.....#','#.....#','##....#','#.#...#','#..#..#','#...#.#','#....##'), # E
    @('#..#..#','#..#..#','#..#..#','.#.#.#.','.#.#.#.','..###..','...#...','...#...','...#...'), # N
    @('#......','##.....','#.#....','#..#...','#......','##.....','#.#....','#..#...','#......'), # D
    @('...#...','..#.#..','.#...#.','#.....#','.#...#.','..#.#..','...#...','..#.#..','.#...#.'), # U
    @('...#...','..#.#..','.#...#.','#.....#','...#..#','...#...','...#...','...#...','...#...'), # R
    @('#......','##.....','#.#....','#..#...','#...#..','#....#.','#.....#','#.....#','#.....#'), # A
    @('...#...','...#...','...#...','...#...','...#...','..#.#..','.#...#.','#.....#','.......'), # S
    @('......#','....#.#','..#...#','#.....#','..#...#','....#.#','......#','......#','......#'), # F
    @('#......','##.....','#.#....','#..#...','#......','#......','#......','#......','#......'), # T
    @('...#...','...#...','...#...','...#...','...#...','...#...','...#...','...#...','...#...'), # I
    @('#...#.#','#..#..#','#.#...#','##....#','#......','#......','#......','#......','#......')  # G
)
$image = [System.Drawing.Bitmap]::new(128, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    for ($glyph = 0; $glyph -lt $glyphs.Count; $glyph++) {
        for ($y = 0; $y -lt 9; $y++) {
            for ($x = 0; $x -lt 7; $x++) {
                if ($glyphs[$glyph][$y][$x] -eq '#') {
                    $image.SetPixel($glyph * 8 + $x, $y + 2, [System.Drawing.Color]::White)
                }
            }
        }
    }
    $destination = Join-Path $PSScriptRoot '../../src/main/resources/assets/lotrcharactercreation/textures/gui/dwarf_cirth.png'
    $directory = Split-Path $destination -Parent
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $image.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $image.Dispose()
}
