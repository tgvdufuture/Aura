param(
    [string]$MessagePath
)

if ([string]::IsNullOrEmpty($MessagePath)) {
    $message = [Console]::In.ReadToEnd()
} else {
    $message = [System.IO.File]::ReadAllText($MessagePath)
}

$message = $message -replace '(?im)^.*Generated with (Codebuff|Freebuff).*\r?\n?', ''
$message = $message -replace '(?im)^.*Co-Authored-By:\s*(Codebuff|Freebuff).*\r?\n?', ''

if ([string]::IsNullOrEmpty($MessagePath)) {
    [Console]::Out.Write($message)
} else {
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($MessagePath, $message, $utf8WithoutBom)
}
