using MPad.Protocol;
using Xunit;

namespace MPad.Protocol.Tests;

public class ProtocolCodecTests
{
    [Fact]
    public void AndroidLowerCamelCasePairRequestDecodesOnWindows()
    {
        var payload = """{"code":"012345","deviceId":"android-1","name":"Phone"}"""u8;

        var request = PairingMessageCodec.DecodePairRequest(payload);

        Assert.NotNull(request);
        Assert.Equal("012345", request.Code);
        Assert.Equal("android-1", request.DeviceId);
        Assert.Equal("Phone", request.Name);
    }

    [Fact]
    public void AndroidLowerCamelCaseAuthRequestDecodesOnWindows()
    {
        var payload = """{"deviceId":"android-1","name":"Phone","token":"AQID"}"""u8;

        var request = PairingMessageCodec.DecodeAuthRequest(payload);

        Assert.NotNull(request);
        Assert.Equal("android-1", request.DeviceId);
        Assert.Equal("Phone", request.Name);
        Assert.Equal("AQID", request.Token);
    }

    [Fact]
    public void GamepadState_RoundTrips_WithBoundaryValues()
    {
        var state = new GamepadState(
            123456789,
            GamepadButtons.A | GamepadButtons.RightShoulder | GamepadButtons.Guide,
            HatDirection.SouthWest,
            short.MinValue, short.MaxValue, -1234, 4321,
            0, 255, 87);

        var decoded = ProtocolCodec.DecodeGamepadState(ProtocolCodec.EncodeGamepadState(state));

        Assert.Equal(state, decoded);
    }

    [Fact]
    public void GamepadState_MatchesCrossPlatformGoldenVector()
    {
        var state = new GamepadState(
            0x0102030405060708,
            GamepadButtons.A | GamepadButtons.LeftShoulder | GamepadButtons.Guide,
            HatDirection.NorthWest,
            short.MinValue, short.MaxValue, -1, 1, 12, 250, 87);
        var expected = Convert.FromHexString("080706050403020111010000070080FF7FFFFF01000CFA57");
        Assert.Equal(expected, ProtocolCodec.EncodeGamepadState(state));
        Assert.Equal(state, ProtocolCodec.DecodeGamepadState(expected));
    }

    [Fact]
    public void AuthenticatedFrame_RoundTrips_AndRejectsTampering()
    {
        var key = Enumerable.Range(0, 32).Select(i => (byte)i).ToArray();
        var frame = ProtocolCodec.Encode(MessageType.InputState, [1, 2, 3], 42, 7, key);

        var decoded = ProtocolCodec.Decode(frame, key);
        Assert.Equal(MessageType.InputState, decoded.Header.Type);
        Assert.Equal((uint)42, decoded.Header.SessionId);
        Assert.Equal(new byte[] { 1, 2, 3 }, decoded.Payload);

        frame[ProtocolConstants.HeaderSize] ^= 0x80;
        Assert.Throws<ProtocolException>(() => ProtocolCodec.Decode(frame, key));
    }

    [Fact]
    public void Rumble_RoundTrips()
    {
        var rumble = new RumbleState(10, 240, 1500, 99);
        Assert.Equal(rumble, ProtocolCodec.DecodeRumble(ProtocolCodec.EncodeRumble(rumble)));
    }

    [Fact]
    public async Task StreamReader_HandlesFragmentedReads()
    {
        var bytes = ProtocolCodec.Encode(MessageType.Ping, [9, 8, 7], sequence: 12);
        await using var stream = new FragmentedStream(bytes, 2);
        var frame = await ProtocolCodec.ReadFromStreamAsync(stream, null, CancellationToken.None);
        Assert.NotNull(frame);
        Assert.Equal(new byte[] { 9, 8, 7 }, frame!.Payload);
    }

    private sealed class FragmentedStream(byte[] data, int fragmentSize) : MemoryStream(data)
    {
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default) =>
            base.ReadAsync(buffer[..Math.Min(fragmentSize, buffer.Length)], cancellationToken);
    }
}
