using System.Runtime.InteropServices;

namespace MPad.Tester;

internal sealed class XInputRuntime : IDisposable
{
    private const uint ErrorSuccess = 0;
    private readonly nint _library;
    private readonly XInputGetStateDelegate _getState;
    private readonly XInputSetStateDelegate _setState;

    public XInputRuntime()
    {
        string[] candidates = ["xinput1_4.dll", "xinput1_3.dll", "xinput9_1_0.dll"];
        foreach (var candidate in candidates)
        {
            if (!NativeLibrary.TryLoad(candidate, out _library))
            {
                continue;
            }

            _getState = Marshal.GetDelegateForFunctionPointer<XInputGetStateDelegate>(
                NativeLibrary.GetExport(_library, "XInputGetState"));
            _setState = Marshal.GetDelegateForFunctionPointer<XInputSetStateDelegate>(
                NativeLibrary.GetExport(_library, "XInputSetState"));
            LibraryName = candidate;
            return;
        }

        throw new DllNotFoundException("Windows XInput 运行库不可用。");
    }

    public string LibraryName { get; }

    public bool TryGetState(uint playerIndex, out GamepadSnapshot snapshot)
    {
        var result = _getState(playerIndex, out var state);
        if (result != ErrorSuccess)
        {
            snapshot = default;
            return false;
        }

        snapshot = new GamepadSnapshot(
            state.PacketNumber,
            state.Gamepad.Buttons,
            state.Gamepad.LeftTrigger,
            state.Gamepad.RightTrigger,
            state.Gamepad.ThumbLX,
            state.Gamepad.ThumbLY,
            state.Gamepad.ThumbRX,
            state.Gamepad.ThumbRY);
        return true;
    }

    public bool SetVibration(uint playerIndex, ushort leftMotor, ushort rightMotor)
    {
        var vibration = new XInputVibration { LeftMotorSpeed = leftMotor, RightMotorSpeed = rightMotor };
        return _setState(playerIndex, ref vibration) == ErrorSuccess;
    }

    public void Dispose()
    {
        if (_library != 0)
        {
            NativeLibrary.Free(_library);
        }
    }

    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    private delegate uint XInputGetStateDelegate(uint playerIndex, out XInputState state);

    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    private delegate uint XInputSetStateDelegate(uint playerIndex, ref XInputVibration vibration);

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputState
    {
        public uint PacketNumber;
        public XInputGamepad Gamepad;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputGamepad
    {
        public ushort Buttons;
        public byte LeftTrigger;
        public byte RightTrigger;
        public short ThumbLX;
        public short ThumbLY;
        public short ThumbRX;
        public short ThumbRY;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputVibration
    {
        public ushort LeftMotorSpeed;
        public ushort RightMotorSpeed;
    }
}

internal readonly record struct GamepadSnapshot(
    uint PacketNumber,
    ushort Buttons,
    byte LeftTrigger,
    byte RightTrigger,
    short LeftX,
    short LeftY,
    short RightX,
    short RightY)
{
    public bool IsPressed(GamepadButton button) => (Buttons & (ushort)button) != 0;

    public static double Normalize(short value) => value >= 0 ? value / 32767d : value / 32768d;
}

[Flags]
internal enum GamepadButton : ushort
{
    DpadUp = 0x0001,
    DpadDown = 0x0002,
    DpadLeft = 0x0004,
    DpadRight = 0x0008,
    Start = 0x0010,
    Back = 0x0020,
    LeftThumb = 0x0040,
    RightThumb = 0x0080,
    LeftShoulder = 0x0100,
    RightShoulder = 0x0200,
    A = 0x1000,
    B = 0x2000,
    X = 0x4000,
    Y = 0x8000
}
