using System.Diagnostics;
using System.IO;

namespace MPad.Companion.Services;

internal static class DriverInstaller
{
    private const string DownloadUrl = "https://github.com/nefarius/ViGEmBus/releases/tag/v1.22.0";

    public static Task InstallOrOpenDownloadAsync()
    {
        var candidates = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "drivers", "ViGEmBus_1.22.0_x64_x86_arm64.exe"),
            Path.Combine(AppContext.BaseDirectory, "drivers", "ViGEmBusSetup_x64.msi"),
        };
        var installer = candidates.FirstOrDefault(File.Exists);
        if (installer is not null)
        {
            Process.Start(new ProcessStartInfo(installer) { UseShellExecute = true, Verb = "runas" });
        }
        else
        {
            Process.Start(new ProcessStartInfo(DownloadUrl) { UseShellExecute = true });
        }
        return Task.CompletedTask;
    }
}
