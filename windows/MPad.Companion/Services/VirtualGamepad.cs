using MPad.Protocol;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace MPad.Companion.Services;

internal sealed class VirtualGamepad : IDisposable
{
    private ViGEmClient? _client;
    private IXbox360Controller? _controller;
    private uint _rumbleId;

    public bool IsAvailable => _controller is not null;
    public string StatusDetail { get; private set; } = "尚未检测";
    public event Action<RumbleState>? RumbleReceived;

    public bool TryInitialize()
    {
        if (_controller is not null) return true;
        try
        {
            _client = new ViGEmClient();
            _controller = _client.CreateXbox360Controller();
            _controller.FeedbackReceived += OnFeedbackReceived;
            _controller.AutoSubmitReport = false;
            _controller.Connect();
            Apply(GamepadState.Neutral);
            StatusDetail = "ViGEmBus 已连接，虚拟 Xbox 360 手柄就绪";
            return true;
        }
        catch (Exception ex)
        {
            StatusDetail = $"ViGEmBus 不可用：{ex.GetType().Name}";
            Dispose();
            return false;
        }
    }

    public void Apply(GamepadState state)
    {
        var c = _controller;
        if (c is null) return;

        Set(c, Xbox360Button.A, state.Buttons.HasFlag(GamepadButtons.A));
        Set(c, Xbox360Button.B, state.Buttons.HasFlag(GamepadButtons.B));
        Set(c, Xbox360Button.X, state.Buttons.HasFlag(GamepadButtons.X));
        Set(c, Xbox360Button.Y, state.Buttons.HasFlag(GamepadButtons.Y));
        Set(c, Xbox360Button.LeftShoulder, state.Buttons.HasFlag(GamepadButtons.LeftShoulder));
        Set(c, Xbox360Button.RightShoulder, state.Buttons.HasFlag(GamepadButtons.RightShoulder));
        Set(c, Xbox360Button.Back, state.Buttons.HasFlag(GamepadButtons.Back));
        Set(c, Xbox360Button.Start, state.Buttons.HasFlag(GamepadButtons.Start));
        Set(c, Xbox360Button.Guide, state.Buttons.HasFlag(GamepadButtons.Guide));
        Set(c, Xbox360Button.LeftThumb, state.Buttons.HasFlag(GamepadButtons.LeftThumb));
        Set(c, Xbox360Button.RightThumb, state.Buttons.HasFlag(GamepadButtons.RightThumb));

        Set(c, Xbox360Button.Up, state.Hat is HatDirection.North or HatDirection.NorthEast or HatDirection.NorthWest);
        Set(c, Xbox360Button.Down, state.Hat is HatDirection.South or HatDirection.SouthEast or HatDirection.SouthWest);
        Set(c, Xbox360Button.Left, state.Hat is HatDirection.West or HatDirection.NorthWest or HatDirection.SouthWest);
        Set(c, Xbox360Button.Right, state.Hat is HatDirection.East or HatDirection.NorthEast or HatDirection.SouthEast);

        c.SetAxisValue(Xbox360Axis.LeftThumbX, state.LeftX);
        c.SetAxisValue(Xbox360Axis.LeftThumbY, state.LeftY);
        c.SetAxisValue(Xbox360Axis.RightThumbX, state.RightX);
        c.SetAxisValue(Xbox360Axis.RightThumbY, state.RightY);
        c.SetSliderValue(Xbox360Slider.LeftTrigger, state.LeftTrigger);
        c.SetSliderValue(Xbox360Slider.RightTrigger, state.RightTrigger);
        c.SubmitReport();
    }

    private static void Set(IXbox360Controller controller, Xbox360Button button, bool value) =>
        controller.SetButtonState(button, value);

    private void OnFeedbackReceived(object? sender, Xbox360FeedbackReceivedEventArgs e) =>
        RumbleReceived?.Invoke(new RumbleState(e.LargeMotor, e.SmallMotor, 250, ++_rumbleId));

    public void Dispose()
    {
        if (_controller is not null)
        {
            try
            {
                _controller.FeedbackReceived -= OnFeedbackReceived;
                _controller.Disconnect();
            }
            catch { }
            _controller = null;
        }
        _client?.Dispose();
        _client = null;
    }
}

