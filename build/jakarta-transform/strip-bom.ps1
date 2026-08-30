<#
  Migration Phase C helper (delete after Phase C).

  Rewrites files as UTF-8 WITHOUT BOM. PowerShell's Set-Content -Encoding UTF8
  writes a BOM that javac rejects ("illegal character: '\ufeff'"). This strips
  the BOM (and normalizes to UTF-8 no-BOM) for the given extensions under Root.

  Only touches files that currently start with a UTF-8 BOM, to avoid needless
  rewrites.
#>
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [string[]]$Extensions = @('.java')
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$files = Get-ChildItem -Path $Root -Recurse -File | Where-Object { $Extensions -contains $_.Extension }
$fixed = 0

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        # Read as UTF-8 (which strips the BOM) and rewrite without BOM.
        $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText($file.FullName, $text, $utf8NoBom)
        $fixed++
    }
}

Write-Output ("Files scanned: {0}" -f $files.Count)
Write-Output ("BOM stripped:  {0}" -f $fixed)
