using System.Text.Json;

namespace MPad.Protocol;

public sealed record PairRequestMessage(string? Code, string? DeviceId, string? Name);
public sealed record AuthRequestMessage(string? DeviceId, string? Name, string? Token);

public static class PairingMessageCodec
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    public static PairRequestMessage? DecodePairRequest(ReadOnlySpan<byte> payload) =>
        JsonSerializer.Deserialize<PairRequestMessage>(payload, Options);

    public static AuthRequestMessage? DecodeAuthRequest(ReadOnlySpan<byte> payload) =>
        JsonSerializer.Deserialize<AuthRequestMessage>(payload, Options);
}
