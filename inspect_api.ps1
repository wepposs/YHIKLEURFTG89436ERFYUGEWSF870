$loom = "$env:USERPROFILE\.gradle\caches\fabric-loom"
$jar = Get-ChildItem -Path $loom -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match 'minecraft' -and $_.Name -notmatch 'sources' -and ($_.Name -match 'named' -or $_.Name -match 'mapped' -or $_.Name -match 'merged' -or $_.Name -match 'intermediary') } |
    Select-Object -First 1
if (-not $jar) {
    Write-Output "NO JAR FOUND"
    Get-ChildItem -Path $loom -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 40 | ForEach-Object { Write-Output $_.FullName }
    exit
}
Write-Output "JAR: $($jar.FullName)"
Write-Output "===== ClickSlotC2SPacket ====="
& javap -cp $jar.FullName -p net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
Write-Output "===== CloseScreenS2CPacket ====="
& javap -cp $jar.FullName -p net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket
Write-Output "===== ScreenHandler (revision/syncId) ====="
& javap -cp $jar.FullName -p net.minecraft.screen.ScreenHandler | Select-String -Pattern "evision|yncId|getRevision"
