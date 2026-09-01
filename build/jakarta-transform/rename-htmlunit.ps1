<#
  Renames legacy HtmlUnit packages to the modern ones in test sources.

  HtmlUnit 3.x (used by com.github.albfernandez.test-jsf:htmlunit-client:10.0.0)
  moved its packages:
    com.gargoylesoftware.htmlunit          -> org.htmlunit
    net.sourceforge.htmlunit.corejs        -> org.htmlunit.corejs

  Writes files back as UTF-8 without BOM. Only touches *.java under the given
  roots, skipping any '\target\' directory.
#>
param(
    [string[]] $Roots = @(
        'components/a4j/src/test',
        'components/rich/src/test'
    )
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Order matters: replace the more specific corejs package before the generic one
# is a non-issue here because the two prefixes are distinct, but we keep corejs
# first for clarity.
$replacements = @(
    @{ From = 'net.sourceforge.htmlunit.corejs'; To = 'org.htmlunit.corejs' },
    @{ From = 'com.gargoylesoftware.htmlunit';   To = 'org.htmlunit' }
)

$changed = 0
foreach ($root in $Roots) {
    if (-not (Test-Path $root)) { continue }
    Get-ChildItem -Path $root -Recurse -Filter *.java |
        Where-Object { $_.FullName -notmatch '\\target\\' } |
        ForEach-Object {
            $path = $_.FullName
            $text = [System.IO.File]::ReadAllText($path)
            $original = $text
            foreach ($r in $replacements) {
                $text = $text.Replace($r.From, $r.To)
            }
            if ($text -ne $original) {
                [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
                Write-Output ("updated: " + $path)
                $changed++
            }
        }
}
Write-Output ("files changed: " + $changed)
