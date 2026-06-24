$word = New-Object -ComObject Word.Application
$word.Visible = $false
$doc = $word.Documents.Add()

# page setup
$doc.PageSetup.TopMargin = 56
$doc.PageSetup.BottomMargin = 56
$doc.PageSetup.LeftMargin = 72
$doc.PageSetup.RightMargin = 72

# title
$r = $doc.Content.Paragraphs.Add().Range
$r.Text = "DouLuo DaLu Idle Legend v2.0"
$r.Font.Size = 28
$r.Font.Bold = $true
$r.Font.Color = 0x8B0000
$r.ParagraphFormat.Alignment = 1
$r.InsertParagraphAfter()

# subtitle
$r = $doc.Content.Paragraphs.Add().Range
$r.Text = "HunHai LiuPai GeXin - HuiHeZhi FangZhi RPG"
$r.Font.Size = 16
$r.Font.Color = 0x666666
$r.ParagraphFormat.Alignment = 1
$r.InsertParagraphAfter()

# separator
$r = $doc.Content.Paragraphs.Add().Range
$r.Text = "=" * 50
$r.Font.Size = 10
$r.Font.Color = 0xCCCCCC
$r.ParagraphFormat.Alignment = 1
$r.InsertParagraphAfter()

function Add-H2($text) {
    $p = $doc.Content.Paragraphs.Add().Range
    $p.Text = $text
    $p.Font.Size = 18
    $p.Font.Bold = $true
    $p.Font.Color = 0x1A5276
    $p.ParagraphFormat.SpaceAfter = 12
    $p.InsertParagraphAfter()
}

function Add-H3($text) {
    $p = $doc.Content.Paragraphs.Add().Range
    $p.Text = $text
    $p.Font.Size = 15
    $p.Font.Bold = $true
    $p.Font.Color = 0x2E86C1
    $p.ParagraphFormat.SpaceAfter = 8
    $p.InsertParagraphAfter()
}

function Add-P($text, $size=12, $bold=$false, $color=0x000000) {
    $p = $doc.Content.Paragraphs.Add().Range
    $p.Text = $text
    $p.Font.Size = $size
    $p.Font.Bold = $bold
    $p.Font.Color = $color
    $p.ParagraphFormat.SpaceAfter = 6
    $p.InsertParagraphAfter()
}

# intro
Add-H2 "Game Introduction"
Add-P "Douluo Dalu: Idle Legend is a turn-based idle RPG based on the classic novel Douluo Dalu by Tang Jia San Shao. Players become Spirit Masters, starting from ShengHun Village on the path to godhood." 12
Add-P "V2.0 introduces the Martial Soul School Reform - freely choose from 6 schools at the start. No more random awakening. Each school has its own soul pool and attribute focus for unique growth." 12 $false 0x444444

# features
Add-H2 "Core Features"

Add-H3 "Six Martial Soul Schools"
Add-P "Freely choose school at start, determines soul pool and attribute bias:" 12
Add-P "  Balance    - All-round dev, dual physical/magic, survival focus" 12 $false 0x1E8449
Add-P "  Physical   - Melee assault, max ATK burst, high physical defense" 12 $false 0x8E44AD
Add-P "  Magic      - Ranged spells, max MATK output, high magic defense" 12 $false 0xE67E22
Add-P "  Support    - Lv.50 + Prestige>=1 unlock, ultimate survival and team buffs" 12 $false 0x888888
Add-P "  Control    - Lv.70 + Prestige>=2 unlock, bind/freeze/stun battlefield control" 12 $false 0x888888
Add-P "  Assassin   - Lv.90 + Prestige>=3 unlock, high crit+dmg one-hit kills" 12 $false 0x888888

Add-H3 "Soul Ring System"
Add-P "9 ring slots (1 unlocked every 20 levels), 5 tiers (100yr->1000yr->10000yr->100000yr->1000000yr), each ring has random affixes and active skills. Free strategy building." 12

Add-H3 "Soul Bone System"
Add-P "6 bone equipment slots (head/arms/body/legs), 4 rarities (1000yr->10000yr->100000yr->Divine), enhanceable to +10, with random passive skills (dmg reduce/lifesteal/dodge/execute etc)." 12

Add-H3 "Turn-Based Multi-Target Combat"
Add-P "1vN wave battles, manual/auto modes. Skills split into physical/magic lines - magic skills penetrate MDEF, physical skills penetrate PDEF. Soul power management + cooldown system for deep strategy." 12

Add-H3 "Stage-Based Maps"
Add-P "8 maps x 15 stages each, Boss every 5 stages. Bosses have unique names and special affixes. Dynamic stage scaling with smooth difficulty curve." 12

Add-H3 "Prestige Inheritance"
Add-P "Unlock after Lv.100, reset level but keep equipment/achievements/codex/shop levels. Each prestige gives +50% all stats. Soul Master talent tree can preserve school and equipment." 12

Add-H3 "Slaughter City Tower"
Add-P "Independent 100-floor tower climb. Permanent +10 ATK per tower boss kill. Late-game core stat source, push your limits." 12

# school stats table
Add-H2 "School Stat Modifiers"
Add-P "(percentages show multiplier on base stats, brackets show bonus crit rate/crit dmg)" 10 $false 0x888888

$t = $doc.Tables.Add($doc.Content.Paragraphs.Add().Range, 7, 8)
$headers = @("School", "HP", "ATK", "MATK", "PDEF", "MDEF", "CritRate", "CritDmg")
for ($c = 0; $c -lt 8; $c++) {
    $t.Cell(1, $c+1).Range.Text = $headers[$c]
    $t.Cell(1, $c+1).Range.Font.Bold = $true
    $t.Cell(1, $c+1).Range.Font.Size = 9
    $t.Cell(1, $c+1).Shading.BackgroundPatternColor = 0x2E86C1
    $t.Cell(1, $c+1).Range.Font.Color = 0xFFFFFF
}

$data = @(
    @("Balance", "105%", "100%", "100%", "105%", "105%", "+5%", "+5%"),
    @("Physical", "100%", "130%", "45%", "115%", "70%", "+8%", "-"),
    @("Magic", "95%", "45%", "130%", "70%", "115%", "-", "+8%"),
    @("Support", "135%", "55%", "55%", "115%", "115%", "-", "-"),
    @("Control", "115%", "80%", "80%", "105%", "105%", "+10%", "-"),
    @("Assassin", "85%", "135%", "45%", "75%", "75%", "+18%", "+35%")
)

for ($r = 0; $r -lt 6; $r++) {
    for ($c = 0; $c -lt 8; $c++) {
        $t.Cell($r+2, $c+1).Range.Text = $data[$r][$c]
        $t.Cell($r+2, $c+1).Range.Font.Size = 9
        $t.Cell($r+2, $c+1).Range.ParagraphFormat.Alignment = 1
    }
}

$t.Range.InsertParagraphAfter()

# formulas
Add-H2 "Core Formulas"
Add-P "Phys Dmg = ATK x (1 - enemyPDEF/(PDEF+200)), min 10%" 12
Add-P "Magic Dmg = MATK x (1 - enemyMDEF/(MDEF+200)), min 10%" 12
Add-P "Crit Dmg = BaseDmg x CritDmg%" 12
Add-P "Stage Multi = 1 + stage x 0.05  |  Map Multi = 1 + mapID x 0.3" 12
Add-P "Breakthrough Cost = 100 x Level^1.4 soul power" 12
Add-P "Soul Power Max = 100 + Level x 5  |  Regen/turn = 5 + Level/10" 12

# target audience
Add-H2 "Target Audience"
Add-P "- Douluo Dalu IP fans" 12
Add-P "- Idle/incremental mobile game lovers" 12
Add-P "- Turn-based RPG and number-crunching strategy fans" 12
Add-P "- Casual players with fragmented time seeking easy growth" 12

# version info
Add-H2 "Version Info"
Add-P "Version: V2.0" 12
Add-P "Platform: Android 9.0+" 12
Add-P "Genre: Turn-Based Idle RPG" 12
Add-P "IP: Douluo Dalu (Tang Jia San Shao)" 12

# footer
$footer = $doc.Sections(1).Footers(1).Range
$footer.Text = "Douluo Dalu Idle Legend V2.0 | School Reform | Turn-Based Idle RPG"
$footer.Font.Size = 8
$footer.Font.Color = 0x999999
$footer.ParagraphFormat.Alignment = 1

# save
$out = "E:\android\testGame\Douluo_Idle_Legend_V2.0.docx"
$doc.SaveAs($out)
$doc.Close()
$word.Quit()
Write-Host "OK"
