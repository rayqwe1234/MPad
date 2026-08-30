namespace MPad.Protocol;

public static class ProtocolConstants
{
    public const byte Version = 1;
    public const int HeaderSize = 20;
    public const int AuthenticationTagSize = 16;
    public const int GamepadPayloadSize = 24;
    public const int DiscoveryPort = 26760;
    public const int ControlPort = 26761;
    public const string BluetoothServiceUuid = "79165E10-9A7B-4C8D-A0E1-4D5041440001";
    public const string MagicText = "MPAD";
}

[Flags]
public enum FrameFlags : ushort
{
    None = 0,
    Authenticated = 1,
}

public enum MessageType : byte
{
    DiscoveryRequest = 1,
    DiscoveryResponse = 2,
    PairRequest = 3,
    PairResponse = 4,
    AuthRequest = 5,
    AuthResponse = 6,
    InputState = 10,
    Rumble = 11,
    Ping = 12,
    Pong = 13,
    Disconnect = 14,
    Error = 15,
}

