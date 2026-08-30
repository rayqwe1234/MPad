namespace MPad.Protocol;

[Flags]
public enum GamepadButtons : uint
{
    None = 0,
    A = 1u << 0,
    B = 1u << 1,
    X = 1u << 2,
    Y = 1u << 3,
    LeftShoulder = 1u << 4,
    RightShoulder = 1u << 5,
    Back = 1u << 6,
    Start = 1u << 7,
    Guide = 1u << 8,
    LeftThumb = 1u << 9,
    RightThumb = 1u << 10,
}

public enum HatDirection : byte
{
    North = 0,
    NorthEast = 1,
    East = 2,
    SouthEast = 3,
    South = 4,
    SouthWest = 5,
    West = 6,
    NorthWest = 7,
    Neutral = 8,
}

public readonly record struct GamepadState(
    long TimestampMicros,
    GamepadButtons Buttons,
    HatDirection Hat,
    short LeftX,
    short LeftY,
    short RightX,
    short RightY,
    byte LeftTrigger,
    byte RightTrigger,
    byte BatteryPercent)
{
    public static GamepadState Neutral => new(
        0, GamepadButtons.None, HatDirection.Neutral,
        0, 0, 0, 0, 0, 0, 255);
}

public readonly record struct RumbleState(byte LowFrequency, byte HighFrequency, ushort DurationMs, uint EventId);

