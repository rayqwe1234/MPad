using InTheHand.Net.Sockets;
using MPad.Protocol;

namespace MPad.Companion.Services;

internal sealed class BluetoothServer : IAsyncDisposable
{
    private readonly CompanionHost _host;
    private readonly CancellationTokenSource _cts = new();
    private BluetoothListener? _listener;
    private Task? _acceptTask;

    public BluetoothServer(CompanionHost host) => _host = host;

    public void Start()
    {
        _listener = new BluetoothListener(Guid.Parse(ProtocolConstants.BluetoothServiceUuid))
        {
            ServiceName = "MPad Companion",
        };
        _listener.Start();
        _acceptTask = Task.Run(AcceptLoop);
    }

    private void AcceptLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            BluetoothClient? client = null;
            try
            {
                client = _listener!.AcceptBluetoothClient();
                var ownedClient = client;
                _ = _host.HandleStreamAsync(client.GetStream(), $"Bluetooth {client.RemoteMachineName}", _cts.Token)
                    .ContinueWith(_ => ownedClient.Dispose(), TaskScheduler.Default);
            }
            catch (Exception ex) when (!_cts.IsCancellationRequested)
            {
                client?.Dispose();
                _host.ReportLog($"蓝牙：{ex.Message}");
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        _listener?.Stop();
        if (_acceptTask is not null)
            try { await _acceptTask.WaitAsync(TimeSpan.FromSeconds(2)); } catch { }
        _cts.Dispose();
    }
}
