using System.Drawing;
using System.Windows;
using Forms = System.Windows.Forms;

namespace MPad.Companion;

public partial class App : System.Windows.Application
{
    private Forms.NotifyIcon? _trayIcon;
    private MainWindow? _window;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        _window = new MainWindow();
        _window.Closing += (_, args) =>
        {
            if (_window.IsExplicitExit) return;
            args.Cancel = true;
            _window.Hide();
        };

        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("打开 MPad", null, (_, _) => ShowWindow());
        menu.Items.Add("退出", null, async (_, _) => await ExitAsync());
        _trayIcon = new Forms.NotifyIcon
        {
            Text = "MPad Companion",
            Icon = SystemIcons.Application,
            Visible = true,
            ContextMenuStrip = menu,
        };
        _trayIcon.DoubleClick += (_, _) => ShowWindow();
        _window.Show();
    }

    private void ShowWindow()
    {
        if (_window is null) return;
        _window.Show();
        _window.WindowState = WindowState.Normal;
        _window.Activate();
    }

    private async Task ExitAsync()
    {
        if (_window is not null)
        {
            _window.IsExplicitExit = true;
            await _window.StopAsync();
            _window.Close();
        }
        if (_trayIcon is not null)
        {
            _trayIcon.Visible = false;
            _trayIcon.Dispose();
        }
        Shutdown();
    }
}
