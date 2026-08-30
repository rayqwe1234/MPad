using System.Net;
using System.Security.Cryptography;
using System.Text.Json;
using System.IO;
using MPad.Protocol;

namespace MPad.Companion.Services;

internal sealed class CompanionHost : IAsyncDisposable
{
    private readonly PairingStore _pairings = new();
    private readonly VirtualGamepad _virtualGamepad = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly object _sessionGate = new();
    private LanServer? _lan;
    private BluetoothServer? _bluetooth;
    private ClientSession? _session;
    private Task? _watchdogTask;

    public CompanionHost()
    {
        PairingCode = GeneratePairingCode();
        _virtualGamepad.RumbleReceived += rumble => _ = SendRumbleAsync(rumble);
    }

    public string PairingCode { get; private set; }
    public event Action<string>? Log;
    public event Action<string>? StatusChanged;
    public event Action<GamepadState>? GamepadStateChanged;

    public async Task StartAsync()
    {
        _virtualGamepad.TryInitialize();
        _lan = new LanServer(this);
        _lan.Start();
        ReportLog("局域网监听已启动。");

        try
        {
            _bluetooth = new BluetoothServer(this);
            _bluetooth.Start();
            ReportLog("蓝牙 RFCOMM 监听已启动。");
        }
        catch (Exception ex)
        {
            _bluetooth = null;
            ReportLog($"蓝牙监听不可用：{ex.Message}");
        }

        _watchdogTask = WatchdogAsync(_cts.Token);
        await Task.CompletedTask;
    }

    public void RefreshPairingCode() => PairingCode = GeneratePairingCode();

    public Task<(bool Installed, string Detail)> CheckDriverAsync()
    {
        var ok = _virtualGamepad.TryInitialize();
        return Task.FromResult((ok, _virtualGamepad.StatusDetail));
    }

    public byte[]? HandleDatagram(byte[] bytes, IPEndPoint remote)
    {
        try
        {
            var header = ProtocolCodec.ReadHeader(bytes.AsSpan(0, Math.Min(bytes.Length, ProtocolConstants.HeaderSize)));
            if (header.Type == MessageType.DiscoveryRequest)
            {
                var request = ProtocolCodec.Decode(bytes);
                var response = JsonSerializer.SerializeToUtf8Bytes(new
                {
                    name = Environment.MachineName,
                    protocolVersion = ProtocolConstants.Version,
                    controlPort = ProtocolConstants.ControlPort,
                    driverReady = _virtualGamepad.IsAvailable,
                });
                return ProtocolCodec.Encode(MessageType.DiscoveryResponse, response);
            }

            var session = GetSession(header.SessionId);
            if (session is null) return null;
            var frame = ProtocolCodec.Decode(bytes, session.Token);
            if (frame.Header.Type == MessageType.InputState)
                HandleInput(session, frame);
        }
        catch (Exception ex)
        {
            ReportLog($"忽略来自 {remote} 的无效数据：{ex.Message}");
        }
        return null;
    }

    public async Task HandleStreamAsync(Stream stream, string transport, CancellationToken serverToken)
    {
        ReportLog($"收到连接：{transport}");
        ClientSession? ownedSession = null;
        try
        {
            while (!serverToken.IsCancellationRequested)
            {
                var frame = await ProtocolCodec.ReadFromStreamAsync(stream,
                    sessionId => GetSession(sessionId)?.Token, serverToken);
                if (frame is null) break;

                switch (frame.Header.Type)
                {
                    case MessageType.PairRequest:
                        ownedSession = await HandlePairRequestAsync(frame.Payload, stream, transport, serverToken);
                        break;
                    case MessageType.AuthRequest:
                        ownedSession = await HandleAuthRequestAsync(frame.Payload, stream, transport, serverToken);
                        break;
                    case MessageType.InputState:
                        if (ownedSession is not null) HandleInput(ownedSession, frame);
                        break;
                    case MessageType.Ping:
                        if (ownedSession is not null)
                            await ownedSession.SendAsync(MessageType.Pong, frame.Payload, serverToken);
                        break;
                    case MessageType.Disconnect:
                        return;
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { ReportLog($"{transport} 连接结束：{ex.Message}"); }
        finally
        {
            if (ownedSession is not null && ReferenceEquals(GetSession(ownedSession.SessionId), ownedSession))
                await EndSessionAsync(ownedSession, "手机已断开");
            else
                try { await stream.DisposeAsync(); } catch { }
        }
    }

    private async Task<ClientSession?> HandlePairRequestAsync(byte[] payload, Stream stream, string transport, CancellationToken token)
    {
        PairRequestMessage? request;
        try { request = PairingMessageCodec.DecodePairRequest(payload); }
        catch { request = null; }
        if (request is null || string.IsNullOrWhiteSpace(request.DeviceId) || request.Code != PairingCode)
        {
            await WriteUnauthenticatedAsync(stream, MessageType.PairResponse,
                new { ok = false, error = "配对码不正确" }, token);
            return null;
        }

        var pairingToken = RandomNumberGenerator.GetBytes(32);
        _pairings.SaveToken(request.DeviceId, pairingToken);
        var session = CreateSession(request.DeviceId, request.Name ?? "Android 手机", pairingToken, stream, transport);
        await WriteUnauthenticatedAsync(stream, MessageType.PairResponse,
            new { ok = true, token = Convert.ToBase64String(pairingToken), sessionId = session.SessionId }, token);
        ReportLog($"已配对 {session.DisplayName}（{transport}）。");
        return session;
    }

    private async Task<ClientSession?> HandleAuthRequestAsync(byte[] payload, Stream stream, string transport, CancellationToken token)
    {
        AuthRequestMessage? request;
        try { request = PairingMessageCodec.DecodeAuthRequest(payload); }
        catch { request = null; }
        var stored = request is null ? null : _pairings.GetToken(request.DeviceId ?? string.Empty);
        byte[]? supplied = null;
        try { if (request?.Token is not null) supplied = Convert.FromBase64String(request.Token); } catch { }
        if (stored is null || supplied is null || !CryptographicOperations.FixedTimeEquals(stored, supplied))
        {
            await WriteUnauthenticatedAsync(stream, MessageType.AuthResponse,
                new { ok = false, error = "需要重新输入配对码" }, token);
            return null;
        }

        var session = CreateSession(request!.DeviceId!, request.Name ?? "Android 手机", stored, stream, transport);
        await WriteUnauthenticatedAsync(stream, MessageType.AuthResponse,
            new { ok = true, sessionId = session.SessionId }, token);
        ReportLog($"{session.DisplayName} 已重新连接（{transport}）。");
        return session;
    }

    private ClientSession CreateSession(string deviceId, string name, byte[] token, Stream stream, string transport)
    {
        var id = (uint)RandomNumberGenerator.GetInt32(1, int.MaxValue);
        var session = new ClientSession(id, deviceId, name, token, stream, transport);
        ClientSession? previous;
        lock (_sessionGate)
        {
            previous = _session;
            _session = session;
        }
        if (previous is not null)
            _ = EndSessionAsync(previous, "已由新连接替换");
        StatusChanged?.Invoke($"已连接：{name} · {transport}");
        return session;
    }

    private void HandleInput(ClientSession session, ProtocolFrame frame)
    {
        if (frame.Header.SessionId != session.SessionId ||
            (session.HasInputSequence && !IsNewer(frame.Header.Sequence, session.LastInputSequence)))
            return;

        var state = ProtocolCodec.DecodeGamepadState(frame.Payload);
        session.LastInputSequence = frame.Header.Sequence;
        session.HasInputSequence = true;
        session.LastInputUtc = DateTime.UtcNow;
        session.Neutralized = false;
        _virtualGamepad.Apply(state);
        GamepadStateChanged?.Invoke(state);
    }

    private async Task SendRumbleAsync(RumbleState rumble)
    {
        var session = GetCurrentSession();
        if (session is null) return;
        try { await session.SendAsync(MessageType.Rumble, ProtocolCodec.EncodeRumble(rumble), _cts.Token); }
        catch (Exception ex) { ReportLog($"发送震动失败：{ex.Message}"); }
    }

    private async Task WatchdogAsync(CancellationToken token)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(50));
        while (await timer.WaitForNextTickAsync(token))
        {
            var session = GetCurrentSession();
            if (session is null) continue;
            var idle = DateTime.UtcNow - session.LastInputUtc;
            if (idle >= TimeSpan.FromMilliseconds(250) && !session.Neutralized)
            {
                session.Neutralized = true;
                _virtualGamepad.Apply(GamepadState.Neutral);
                GamepadStateChanged?.Invoke(GamepadState.Neutral);
                ReportLog("输入超时，已释放全部按键。");
            }
            if (idle >= TimeSpan.FromSeconds(2))
                await EndSessionAsync(session, "输入连接超时");
        }
    }

    private async Task EndSessionAsync(ClientSession session, string reason)
    {
        var wasCurrent = false;
        lock (_sessionGate)
        {
            if (ReferenceEquals(_session, session))
            {
                _session = null;
                wasCurrent = true;
            }
        }
        if (wasCurrent)
        {
            _virtualGamepad.Apply(GamepadState.Neutral);
            GamepadStateChanged?.Invoke(GamepadState.Neutral);
            StatusChanged?.Invoke("尚无手机连接");
            ReportLog(reason);
        }
        await session.DisposeAsync();
    }

    private ClientSession? GetCurrentSession() { lock (_sessionGate) return _session; }
    private ClientSession? GetSession(uint sessionId) { lock (_sessionGate) return _session?.SessionId == sessionId ? _session : null; }
    private static bool IsNewer(uint value, uint previous) => unchecked((int)(value - previous)) > 0;
    private static string GeneratePairingCode() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    private static async Task WriteUnauthenticatedAsync(Stream stream, MessageType type, object data, CancellationToken token)
    {
        var frame = ProtocolCodec.Encode(type, JsonSerializer.SerializeToUtf8Bytes(data));
        await stream.WriteAsync(frame, token);
        await stream.FlushAsync(token);
    }

    public void ReportLog(string message) => Log?.Invoke(message);

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        var session = GetCurrentSession();
        if (session is not null) await EndSessionAsync(session, "伴侣程序已停止");
        if (_bluetooth is not null) await _bluetooth.DisposeAsync();
        if (_lan is not null) await _lan.DisposeAsync();
        if (_watchdogTask is not null) try { await _watchdogTask; } catch { }
        _virtualGamepad.Dispose();
        _cts.Dispose();
    }

}
