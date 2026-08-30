using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace MPad.Protocol;

public static class ProtocolCodec
{
    private static readonly byte[] Magic = Encoding.ASCII.GetBytes(ProtocolConstants.MagicText);

    public static byte[] Encode(
        MessageType type,
        ReadOnlySpan<byte> payload,
        uint sessionId = 0,
        uint sequence = 0,
        ReadOnlySpan<byte> authenticationKey = default)
    {
        if (payload.Length > ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(payload));

        var authenticated = !authenticationKey.IsEmpty;
        var frame = new byte[ProtocolConstants.HeaderSize + payload.Length +
                             (authenticated ? ProtocolConstants.AuthenticationTagSize : 0)];
        Magic.CopyTo(frame, 0);
        frame[4] = ProtocolConstants.Version;
        frame[5] = (byte)type;
        BinaryPrimitives.WriteUInt16LittleEndian(frame.AsSpan(6, 2),
            authenticated ? (ushort)FrameFlags.Authenticated : (ushort)FrameFlags.None);
        BinaryPrimitives.WriteUInt16LittleEndian(frame.AsSpan(8, 2), (ushort)payload.Length);
        frame[10] = 0;
        frame[11] = 0;
        BinaryPrimitives.WriteUInt32LittleEndian(frame.AsSpan(12, 4), sessionId);
        BinaryPrimitives.WriteUInt32LittleEndian(frame.AsSpan(16, 4), sequence);
        payload.CopyTo(frame.AsSpan(ProtocolConstants.HeaderSize));

        if (authenticated)
        {
            using var hmac = new HMACSHA256(authenticationKey.ToArray());
            var contentLength = ProtocolConstants.HeaderSize + payload.Length;
            var hash = hmac.ComputeHash(frame, 0, contentLength);
            hash.AsSpan(0, ProtocolConstants.AuthenticationTagSize).CopyTo(frame.AsSpan(contentLength));
        }

        return frame;
    }

    public static ProtocolFrame Decode(ReadOnlySpan<byte> frame, ReadOnlySpan<byte> authenticationKey = default)
    {
        if (frame.Length < ProtocolConstants.HeaderSize)
            throw new ProtocolException("Frame is shorter than the protocol header.");
        if (!frame[..4].SequenceEqual(Magic))
            throw new ProtocolException("Invalid MPad frame magic.");

        var header = ReadHeader(frame[..ProtocolConstants.HeaderSize]);
        if (header.Version != ProtocolConstants.Version)
            throw new ProtocolException($"Unsupported protocol version {header.Version}.");

        var authenticated = header.Flags.HasFlag(FrameFlags.Authenticated);
        var expectedLength = ProtocolConstants.HeaderSize + header.PayloadLength +
                             (authenticated ? ProtocolConstants.AuthenticationTagSize : 0);
        if (frame.Length != expectedLength)
            throw new ProtocolException($"Invalid frame length. Expected {expectedLength}, got {frame.Length}.");

        if (authenticated)
        {
            if (authenticationKey.IsEmpty)
                throw new ProtocolException("Authenticated frame received without a pairing key.");
            var contentLength = ProtocolConstants.HeaderSize + header.PayloadLength;
            using var hmac = new HMACSHA256(authenticationKey.ToArray());
            var expected = hmac.ComputeHash(frame[..contentLength].ToArray());
            if (!CryptographicOperations.FixedTimeEquals(
                    expected.AsSpan(0, ProtocolConstants.AuthenticationTagSize),
                    frame.Slice(contentLength, ProtocolConstants.AuthenticationTagSize)))
                throw new ProtocolException("Frame authentication failed.");
        }

        return new ProtocolFrame(header, frame.Slice(ProtocolConstants.HeaderSize, header.PayloadLength).ToArray());
    }

    public static ProtocolHeader ReadHeader(ReadOnlySpan<byte> header)
    {
        if (header.Length < ProtocolConstants.HeaderSize || !header[..4].SequenceEqual(Magic))
            throw new ProtocolException("Invalid MPad protocol header.");

        return new ProtocolHeader(
            header[4],
            (MessageType)header[5],
            (FrameFlags)BinaryPrimitives.ReadUInt16LittleEndian(header.Slice(6, 2)),
            BinaryPrimitives.ReadUInt16LittleEndian(header.Slice(8, 2)),
            BinaryPrimitives.ReadUInt32LittleEndian(header.Slice(12, 4)),
            BinaryPrimitives.ReadUInt32LittleEndian(header.Slice(16, 4)));
    }

    public static byte[] EncodeGamepadState(GamepadState state)
    {
        var data = new byte[ProtocolConstants.GamepadPayloadSize];
        BinaryPrimitives.WriteInt64LittleEndian(data.AsSpan(0, 8), state.TimestampMicros);
        BinaryPrimitives.WriteUInt32LittleEndian(data.AsSpan(8, 4), (uint)state.Buttons);
        data[12] = (byte)state.Hat;
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(13, 2), state.LeftX);
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(15, 2), state.LeftY);
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(17, 2), state.RightX);
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(19, 2), state.RightY);
        data[21] = state.LeftTrigger;
        data[22] = state.RightTrigger;
        data[23] = state.BatteryPercent;
        return data;
    }

    public static GamepadState DecodeGamepadState(ReadOnlySpan<byte> data)
    {
        if (data.Length != ProtocolConstants.GamepadPayloadSize)
            throw new ProtocolException("Invalid gamepad payload length.");
        var hat = (HatDirection)data[12];
        if (hat > HatDirection.Neutral)
            throw new ProtocolException("Invalid hat direction.");

        return new GamepadState(
            BinaryPrimitives.ReadInt64LittleEndian(data[..8]),
            (GamepadButtons)BinaryPrimitives.ReadUInt32LittleEndian(data.Slice(8, 4)),
            hat,
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(13, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(15, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(17, 2)),
            BinaryPrimitives.ReadInt16LittleEndian(data.Slice(19, 2)),
            data[21], data[22], data[23]);
    }

    public static byte[] EncodeRumble(RumbleState state)
    {
        var data = new byte[8];
        data[0] = state.LowFrequency;
        data[1] = state.HighFrequency;
        BinaryPrimitives.WriteUInt16LittleEndian(data.AsSpan(2, 2), state.DurationMs);
        BinaryPrimitives.WriteUInt32LittleEndian(data.AsSpan(4, 4), state.EventId);
        return data;
    }

    public static RumbleState DecodeRumble(ReadOnlySpan<byte> data)
    {
        if (data.Length != 8)
            throw new ProtocolException("Invalid rumble payload length.");
        return new RumbleState(data[0], data[1],
            BinaryPrimitives.ReadUInt16LittleEndian(data.Slice(2, 2)),
            BinaryPrimitives.ReadUInt32LittleEndian(data.Slice(4, 4)));
    }

    public static async Task<ProtocolFrame?> ReadFromStreamAsync(
        Stream stream,
        Func<uint, byte[]?>? keyResolver,
        CancellationToken cancellationToken)
    {
        var headerBytes = new byte[ProtocolConstants.HeaderSize];
        if (!await ReadExactlyOrEndAsync(stream, headerBytes, cancellationToken))
            return null;
        var header = ReadHeader(headerBytes);
        var tailLength = header.PayloadLength +
                         (header.Flags.HasFlag(FrameFlags.Authenticated)
                             ? ProtocolConstants.AuthenticationTagSize : 0);
        var full = new byte[ProtocolConstants.HeaderSize + tailLength];
        headerBytes.CopyTo(full, 0);
        await stream.ReadExactlyAsync(full.AsMemory(ProtocolConstants.HeaderSize, tailLength), cancellationToken);
        var key = keyResolver?.Invoke(header.SessionId);
        return Decode(full, key ?? []);
    }

    private static async Task<bool> ReadExactlyOrEndAsync(Stream stream, byte[] buffer, CancellationToken token)
    {
        var offset = 0;
        while (offset < buffer.Length)
        {
            var count = await stream.ReadAsync(buffer.AsMemory(offset), token);
            if (count == 0)
            {
                if (offset == 0) return false;
                throw new EndOfStreamException("Stream ended inside an MPad frame.");
            }
            offset += count;
        }
        return true;
    }
}
