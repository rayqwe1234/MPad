namespace MPad.Protocol;

public readonly record struct ProtocolHeader(
    byte Version,
    MessageType Type,
    FrameFlags Flags,
    ushort PayloadLength,
    uint SessionId,
    uint Sequence);

public sealed record ProtocolFrame(ProtocolHeader Header, byte[] Payload);

public sealed class ProtocolException(string message) : Exception(message);

