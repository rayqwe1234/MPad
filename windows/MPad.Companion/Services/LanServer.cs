using System.Net;
using System.Net.Sockets;
using MPad.Protocol;

namespace MPad.Companion.Services;

internal sealed class LanServer : IAsyncDisposable
{
    private readonly CompanionHost _host;
    private readonly CancellationTokenSource _cts = new();
    private UdpClient? _udp;
    private TcpListener? _tcp;
    private Task? _udpTask;
    private Task? _tcpTask;

    public LanServer(CompanionHost host) => _host = host;

    public void Start()
    {
        _udp = new UdpClient(new IPEndPoint(IPAddress.Any, ProtocolConstants.DiscoveryPort));
        _udp.EnableBroadcast = true;
        _tcp = new TcpListener(IPAddress.Any, ProtocolConstants.ControlPort);
        _tcp.Start();
        _udpTask = RunUdpAsync(_cts.Token);
        _tcpTask = RunTcpAsync(_cts.Token);
    }

    private async Task RunUdpAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var result = await _udp!.ReceiveAsync(token);
                var response = _host.HandleDatagram(result.Buffer, result.RemoteEndPoint);
                if (response is not null)
                    await _udp.SendAsync(response, result.RemoteEndPoint, token);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { _host.ReportLog($"UDP：{ex.Message}"); }
        }
    }

    private async Task RunTcpAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var client = await _tcp!.AcceptTcpClientAsync(token);
                client.NoDelay = true;
                _ = _host.HandleStreamAsync(client.GetStream(), $"LAN {client.Client.RemoteEndPoint}", token)
                    .ContinueWith(_ => client.Dispose(), TaskScheduler.Default);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { _host.ReportLog($"TCP：{ex.Message}"); }
        }
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        _udp?.Dispose();
        _tcp?.Stop();
        var tasks = new[] { _udpTask, _tcpTask }.OfType<Task>().ToArray();
        try { await Task.WhenAll(tasks); } catch { }
        _cts.Dispose();
    }
}

