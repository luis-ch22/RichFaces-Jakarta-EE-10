<#
  Migration Phase C helper (delete after Phase C).

  Renames Jakarta EE namespaces javax.* -> jakarta.* across a source tree using
  an EXPLICIT allow-list of package prefixes. This is safer than a blanket
  replace because it will NOT touch javax.* packages that stayed in the JDK
  (swing, imageio, naming, crypto, sql, sound, xml.parsers, xml.transform,
  xml.xpath, xml.namespace, xml.datatype, annotation.processing, etc.).

  Special cases NOT handled here (left for manual review):
    - javax.xml.rpc  (JAX-RPC; no Jakarta Web Profile equivalent)

  Usage:
    powershell -File rename-javax-to-jakarta.ps1 -Root <dir> [-Extensions .java,.xml] [-WhatIf]

  It processes files with the given extensions, replacing whole-package tokens.
#>
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [string[]]$Extensions = @('.java'),
    [switch]$WhatIf
)

# Package prefixes that migrate javax.<x> -> jakarta.<x>.
# Order matters only for reporting; replacements are independent.
$prefixes = @(
    'faces',
    'servlet',
    'el',
    'enterprise',
    'inject',
    'validation',
    'persistence',
    'jms',
    'activation',
    'mail',
    'transaction',
    'interceptor',
    'ejb',
    'websocket',
    'batch',
    'json',
    'security.enterprise',
    'faces.annotation'
)

# javax.annotation is special: the common annotations (PostConstruct, PreDestroy,
# Generated, Resource, ...) move to jakarta.annotation, BUT javax.annotation.processing
# stays in the JDK. Handle javax.annotation.* EXCEPT javax.annotation.processing.

$files = Get-ChildItem -Path $Root -Recurse -File | Where-Object { $Extensions -contains $_.Extension }

# UTF-8 WITHOUT BOM. Set-Content -Encoding UTF8 writes a BOM that javac rejects
# ("illegal character: '\ufeff'"), so we write bytes ourselves.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$changedFiles = 0
$totalReplacements = 0

foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ($null -eq $content) { continue }
    $original = $content

    foreach ($p in $prefixes) {
        # Replace 'javax.<prefix>' with 'jakarta.<prefix>' only when followed by
        # a '.' or word boundary that indicates a package path (dot or semicolon
        # or whitespace/paren for FQNs in javadoc/annotations).
        $pattern = "javax\.$([regex]::Escape($p))(?=[\.\s;,\)\<\>\*])"
        $content = [regex]::Replace($content, $pattern, "jakarta.$p")
    }

    # javax.annotation.* -> jakarta.annotation.*  EXCEPT javax.annotation.processing
    # Do the processing-safe replacement: temporarily protect .processing
    $content = $content -replace 'javax\.annotation\.processing', '__JAVAX_ANNOTATION_PROCESSING__'
    $content = [regex]::Replace($content, 'javax\.annotation(?=[\.\s;,\)\<\>\*])', 'jakarta.annotation')
    $content = $content -replace '__JAVAX_ANNOTATION_PROCESSING__', 'javax.annotation.processing'

    if ($content -ne $original) {
        $changedFiles++
        # crude replacement count
        $diff = ([regex]::Matches($original, 'javax\.')).Count - ([regex]::Matches($content, 'javax\.')).Count
        $totalReplacements += $diff
        if (-not $WhatIf) {
            [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        }
        Write-Output ("CHANGED [{0} javax->jakarta] {1}" -f $diff, $file.FullName.Substring($Root.Length))
    }
}

Write-Output ""
Write-Output ("Files scanned:  {0}" -f $files.Count)
Write-Output ("Files changed:  {0}" -f $changedFiles)
Write-Output ("javax-> jakarta net token reduction: {0}" -f $totalReplacements)
if ($WhatIf) { Write-Output "(WhatIf mode: no files were written)" }
