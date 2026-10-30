param([Parameter(Mandatory=$true)][string]$Java,[Parameter(Mandatory=$true)][string]$Root,[string[]]$Platforms=@('paper','purpur','folia'),[string[]]$AdditionalCommands=@())
$ErrorActionPreference='Stop'
$runs=@()
foreach($platform in $Platforms) {
    $info=[System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$Java
    $info.WorkingDirectory=Join-Path $Root $platform
    foreach($arg in @('-Xms256M','-Xmx768M','--enable-native-access=ALL-UNNAMED','-jar','server.jar','--nogui')) {$info.ArgumentList.Add($arg)}
    # Windows sandbox AF_UNIX connect fails on this host. A long socket directory
    # makes the JDK's documented TCP fallback path handle selector wakeups instead.
    $info.ArgumentList.Insert(0,"-Djdk.net.unixdomain.tmpdir=$Root")
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $proc=[System.Diagnostics.Process]::Start($info)
    $runs+=@{Platform=$platform;Process=$proc;Out=$proc.StandardOutput.ReadToEndAsync();Err=$proc.StandardError.ReadToEndAsync();Ready=$false;Start=[datetime]::UtcNow}
}
while($runs.Count -gt 0) {
    foreach($run in @($runs)) {
        $proc=$run.Process;$log=Join-Path $Root "$($run.Platform)/logs/latest.log"
        if(!$run.Ready -and (Test-Path $log) -and (Get-Item $log).LastWriteTimeUtc -gt $run.Start -and (Select-String -Path $log -SimpleMatch 'Done (' -Quiet)) {
            $run.Ready=$true
            foreach($cmd in (@('version','plugins','clan help','clan list','clanadmin help','clanadmin reload','clanschat reload','clan create ConsoleInvalid') + $AdditionalCommands)) {$proc.StandardInput.WriteLine($cmd)}
            $proc.StandardInput.Flush()
            $run.StopAt=[datetime]::UtcNow.AddSeconds(5)
            Write-Output "$($run.Platform): startup ready; console checks sent"
        }
        if($run.Ready -and [datetime]::UtcNow -gt $run.StopAt -and !$run.ContainsKey('Stopped')) {$proc.StandardInput.WriteLine('stop');$proc.StandardInput.Flush();$run.Stopped=$true}
        if(!$proc.HasExited -and ([datetime]::UtcNow-$run.Start).TotalSeconds -gt 180) {$proc.Kill($true);Write-Output "$($run.Platform): timeout"}
        if($proc.HasExited) {
            $run.Out.Result|Set-Content (Join-Path $Root "$($run.Platform)/smoke-stdout.log")
            $run.Err.Result|Set-Content (Join-Path $Root "$($run.Platform)/smoke-stderr.log")
            Write-Output "$($run.Platform): exit $($proc.ExitCode), ready=$($run.Ready)"
            $runs=@($runs|Where-Object {$_ -ne $run})
        }
    }
    Start-Sleep -Milliseconds 500
}
