using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace MPad.Tester;

public partial class MainWindow : Window
{
    private static readonly (GamepadButton Button, string Name)[] ButtonNames =
    [
        (GamepadButton.A, "A"), (GamepadButton.B, "B"), (GamepadButton.X, "X"), (GamepadButton.Y, "Y"),
        (GamepadButton.DpadUp, "↑"), (GamepadButton.DpadDown, "↓"),
        (GamepadButton.DpadLeft, "←"), (GamepadButton.DpadRight, "→"),
        (GamepadButton.LeftShoulder, "LB"), (GamepadButton.RightShoulder, "RB"),
        (GamepadButton.LeftThumb, "LS"), (GamepadButton.RightThumb, "RS"),
        (GamepadButton.Back, "BACK"), (GamepadButton.Start, "START")
    ];

    private readonly DispatcherTimer _pollTimer = new() { Interval = TimeSpan.FromMilliseconds(8) };
    private XInputRuntime? _xinput;
    private uint _playerIndex;
    private bool _connected;
    private uint _lastPacket;
    private int _updatesThisSecond;
    private int _updatesPerSecond;
    private DateTime _rateWindow = DateTime.UtcNow;
    private bool _closing;

    public MainWindow()
    {
        InitializeComponent();
        SourceInitialized += (_, _) => WindowTheme.EnableDarkTitleBar(this);
        Loaded += OnLoaded;
        Closing += OnClosing;
        _pollTimer.Tick += PollController;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            _xinput = new XInputRuntime();
            Title = $"MPad 手柄测试器 · {_xinput.LibraryName}";
            _pollTimer.Start();
            PollController(null, EventArgs.Empty);
        }
        catch (Exception ex)
        {
            StatusText.Text = ex.Message;
            StatusDot.Fill = new SolidColorBrush(Color.FromRgb(255, 91, 91));
        }
    }

    private void PollController(object? sender, EventArgs e)
    {
        if (_xinput is null)
        {
            return;
        }

        var connected = _xinput.TryGetState(_playerIndex, out var state);
        if (connected)
        {
            if (!_connected)
            {
                _lastPacket = state.PacketNumber;
                _updatesThisSecond = 0;
                _rateWindow = DateTime.UtcNow;
            }
            else if (state.PacketNumber != _lastPacket)
            {
                _updatesThisSecond++;
                _lastPacket = state.PacketNumber;
            }

            UpdateRate();
            UpdateConnectedUi(state);
        }
        else
        {
            if (_connected)
            {
                ResetMotorControls();
            }
            UpdateDisconnectedUi();
        }

        _connected = connected;
        Visualizer.Update(state, connected);
    }

    private void UpdateConnectedUi(GamepadSnapshot state)
    {
        StatusDot.Fill = new SolidColorBrush(Color.FromRgb(112, 240, 160));
        StatusText.Text = $"手柄 {_playerIndex + 1} 已连接";
        ButtonsText.Text = string.Join("  ", ButtonNames.Where(item => state.IsPressed(item.Button)).Select(item => item.Name)) is { Length: > 0 } names ? names : "—";
        LeftStickText.Text = $"X {state.LeftX,6}\nY {state.LeftY,6}";
        RightStickText.Text = $"X {state.RightX,6}\nY {state.RightY,6}";
        TriggersText.Text = $"LT {state.LeftTrigger,3}    RT {state.RightTrigger,3}";
        PacketText.Text = $"{state.PacketNumber}  ·  {_updatesPerSecond} 更新/秒";
    }

    private void UpdateDisconnectedUi()
    {
        StatusDot.Fill = new SolidColorBrush(Color.FromRgb(106, 116, 130));
        StatusText.Text = $"手柄 {_playerIndex + 1} 未连接";
        ButtonsText.Text = "—";
        LeftStickText.Text = "X      0\nY      0";
        RightStickText.Text = "X      0\nY      0";
        TriggersText.Text = "LT   0    RT   0";
        PacketText.Text = "0  ·  0 更新/秒";
        _updatesPerSecond = 0;
    }

    private void UpdateRate()
    {
        var now = DateTime.UtcNow;
        var elapsed = now - _rateWindow;
        if (elapsed < TimeSpan.FromSeconds(1))
        {
            return;
        }

        _updatesPerSecond = (int)Math.Round(_updatesThisSecond / elapsed.TotalSeconds);
        _updatesThisSecond = 0;
        _rateWindow = now;
    }

    private void PlayerSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var newIndex = (uint)Math.Max(0, PlayerSelector.SelectedIndex);
        if (!IsInitialized || LeftMotorSlider is null || RightMotorSlider is null)
        {
            _playerIndex = newIndex;
            return;
        }

        if (_xinput is not null && _connected)
        {
            _xinput.SetVibration(_playerIndex, 0, 0);
        }
        _playerIndex = newIndex;
        _connected = false;
        _lastPacket = 0;
        _updatesThisSecond = 0;
        _updatesPerSecond = 0;
        ResetMotorControls();
    }

    private void MotorSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (LeftMotorText is null || RightMotorText is null)
        {
            return;
        }

        LeftMotorText.Text = $"{LeftMotorSlider.Value:0}%";
        RightMotorText.Text = $"{RightMotorSlider.Value:0}%";
        ApplyVibration();
    }

    private async void BothMotors_Click(object sender, RoutedEventArgs e)
    {
        if (!_connected)
        {
            StatusText.Text = "请先连接一个 XInput 手柄";
            return;
        }

        LeftMotorSlider.Value = 70;
        RightMotorSlider.Value = 70;
        await Task.Delay(600);
        if (!_closing)
        {
            ResetMotorControls();
        }
    }

    private void StopMotors_Click(object sender, RoutedEventArgs e) => ResetMotorControls();

    private void ApplyVibration()
    {
        if (_xinput is null || !_connected)
        {
            return;
        }

        var left = (ushort)Math.Round(LeftMotorSlider.Value / 100d * ushort.MaxValue);
        var right = (ushort)Math.Round(RightMotorSlider.Value / 100d * ushort.MaxValue);
        _xinput.SetVibration(_playerIndex, left, right);
    }

    private void ResetMotorControls()
    {
        if (LeftMotorSlider is null || RightMotorSlider is null)
        {
            return;
        }

        LeftMotorSlider.Value = 0;
        RightMotorSlider.Value = 0;
        _xinput?.SetVibration(_playerIndex, 0, 0);
    }

    private void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        _closing = true;
        _pollTimer.Stop();
        _xinput?.SetVibration(_playerIndex, 0, 0);
        _xinput?.Dispose();
    }
}
