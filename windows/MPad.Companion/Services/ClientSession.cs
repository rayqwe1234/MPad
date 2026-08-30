using MPad.Protocol;
using System.IO;

namespace MPad.Companion.Services;

internal sealed class ClientSession : IAsyncDisposable
{
    private readonly SemaphoreSlim _writeGate = new(1, 1);
    private readonly Stream _stream;
    private uint _outgoingSequence;

    public ClientSession(uint sessionId, string deviceId, string displayName, byte[] token, Stream stream, string transport)
    {
        SessionId = sessionId;
        DeviceId = deviceId;
        DisplayName = displayName;
        Token = token;
        _stream = stream;
        Transport = transport;
        LastInputUtc = DateTime.UtcNow;
    }

    public uint SessionId { get; }
    public string DeviceId { get; }
    public string DisplayName { get; }
    public byte[] Token { get; }
    public string Transport { get; }
    public uint LastInputSequence { get; set; }
    public bool HasInputSequence { get; set; }
    public DateTime LastInputUtc { get; set; }
    public bool Neutralized { get; set; }

    public async Task SendAsync(MessageType type, byte[] payload, CancellationToken token)
    {
        var frame = ProtocolCodec.Encode(type, payload, SessionId, ++_outgoingSequence, Token);
        await _writeGate.WaitAsync(token);
        try
        {
            await _stream.WriteAsync(frame, token);
            await _stream.FlushAsync(token);
        }
        finally
        {
            _writeGate.Release();
        }
    }

    public async ValueTask DisposeAsync()
    {
        try { await _stream.DisposeAsync(); } catch { }
        _writeGate.Dispose();
    }
}
