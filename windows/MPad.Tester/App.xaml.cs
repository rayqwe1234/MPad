using System.Windows;
using System.Windows.Threading;

namespace MPad.Tester;

public partial class App : Application
{
    public App()
    {
        DispatcherUnhandledException += OnDispatcherUnhandledException;
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show(
            $"MPad 手柄测试器发生错误：\n\n{e.Exception.GetBaseException().Message}",
            "MPad 手柄测试器",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
        Shutdown(1);
    }
}
