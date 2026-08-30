using System.Diagnostics;
using System.Net;
using System.Windows;
using MPad.Companion.Services;
using MPad.Protocol;

namespace MPad.Companion;

public partial class MainWindow : Window
{
    private readonly CompanionHost _host;
    public bool IsExplicitExit { get; set; }

    public MainWindow()
    {
        InitializeComponent();
        _host = new CompanionHost();
        _host.Log += message => Dispatcher.Invoke(() => AppendLog(message));
        _host.StatusChanged += status => Dispatcher.Invoke(() => ConnectionStatusText.Text = status);
        _host.GamepadStateChanged += state => Dispatcher.Invoke(() => ShowState(state));
        SourceInitialized += (_, _) => WindowTheme.EnableDarkTitleBar(this);
        Loaded += async (_, _) => await StartAsync();
    }

    private async Task StartAsync()
    {
        PairingCodeText.Text = _host.PairingCode;
        NetworkInfoText.Text = $"电脑名称：{Environment.MachineName}\nUDP {ProtocolConstants.DiscoveryPort} / TCP {ProtocolConstants.ControlPort}\n局域网优先，蓝牙 RFCOMM 备用";
        try
        {
            await _host.StartAsync();
            HostStatusText.Text = "✓ 局域网与蓝牙监听已启动";
        }
        catch (Exception ex)
        {
            HostStatusText.Text = "部分监听启动失败，请查看日志";
            AppendLog(ex.ToString());
        }
        await RefreshDriverStatusAsync();
    }

    public async Task StopAsync() => await _host.DisposeAsync();

    private void RefreshCode_Click(object sender, RoutedEventArgs e)
    {
        _host.RefreshPairingCode();
        PairingCodeText.Text = _host.PairingCode;
        AppendLog("已刷新首次配对码。");
    }

    private async void InstallDriver_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            await DriverInstaller.InstallOrOpenDownloadAsync();
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show(this, ex.Message, "无法启动驱动安装", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        await RefreshDriverStatusAsync();
    }

    private async Task RefreshDriverStatusAsync()
    {
        var (installed, detail) = await _host.CheckDriverAsync();
        DriverStatusText.Text = installed ? $"✓ {detail}" : $"⚠ {detail}";
        DriverStatusText.Foreground = installed
            ? (System.Windows.Media.Brush)FindResource("AccentBrush")
            : System.Windows.Media.Brushes.Orange;
    }

    private void ShowState(GamepadState state)
    {
        InputStateText.Text =
            $"Buttons: {state.Buttons}  Hat: {state.Hat}\n" +
            $"Sticks:  L({state.LeftX,6}, {state.LeftY,6})  R({state.RightX,6}, {state.RightY,6})\n" +
            $"Triggers: LT {state.LeftTrigger,3}  RT {state.RightTrigger,3}   Battery: {(state.BatteryPercent == 255 ? "—" : state.BatteryPercent + "%")}";
    }

    private void AppendLog(string message)
    {
        LogTextBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}\n");
        LogTextBox.ScrollToEnd();
    }
}
