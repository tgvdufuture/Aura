$inputText = [Console]::In.ReadToEnd()
$zeroSha = '0' * 40

foreach ($line in ($inputText -split "\r?\n")) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    $parts = $line -split ' '
    if ($parts.Count -lt 4) {
        continue
    }

    $localSha = $parts[1]
    $remoteSha = $parts[3]
    if ($localSha -eq $zeroSha) {
        continue
    }

    if ($remoteSha -eq $zeroSha) {
        $commitRange = $localSha
    } else {
        $commitRange = "$remoteSha..$localSha"
    }

    $messages = git log --format='%B' $commitRange 2>$null | Out-String
    if ($messages -match '(?im)Generated with (Codebuff|Freebuff)|Co-Authored-By:\s*(Codebuff|Freebuff)') {
        [Console]::Error.WriteLine('Push blocked: remove Codebuff/Freebuff attribution before pushing.')
        exit 1
    }
}

exit 0
