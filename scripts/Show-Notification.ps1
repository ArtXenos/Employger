# Shows a Windows notification (bottom-right of the screen) with a title
# and a message. Built into Windows 10 and 11; nothing to install.
# Dot-source it, then call:  Show-Notification "Title" "Message"
#
# A notification needs someone logged on; if it can't be shown, the
# caller's log line is still written.

function Show-Notification([string]$Title, [string]$Message) {
    try {
        $null = [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]
        $null = [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime]

        $escape = { param($s) [System.Security.SecurityElement]::Escape($s) }
        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
        $xml.LoadXml("<toast scenario='reminder'><visual><binding template='ToastGeneric'>" +
            "<text>$(& $escape $Title)</text><text>$(& $escape $Message)</text>" +
            "</binding></visual><actions><action content='Dismiss' arguments='dismiss' activationType='system'/></actions></toast>")

        # Shown under Windows PowerShell's registered app ID.
        $appId = '{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe'
        [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($appId).Show(
            [Windows.UI.Notifications.ToastNotification]::new($xml))
    } catch {
        Write-Warning "Could not show a notification: $($_.Exception.Message)"
    }
}
