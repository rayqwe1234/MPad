using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace MPad.Companion;

internal static class WindowTheme
{
    public static void EnableDarkTitleBar(Window window)
    {
        if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 17763)) return;
        var enabled = 1;
        var handle = new WindowInteropHelper(window).Handle;
        if (DwmSetWindowAttribute(handle, 20, ref enabled, sizeof(int)) != 0)
            _ = DwmSetWindowAttribute(handle, 19, ref enabled, sizeof(int));
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(nint window, int attribute, ref int value, int size);
}
