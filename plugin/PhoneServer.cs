using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneServer(Feed feed, Configuration config) : IDisposable
{
    private static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan KeepAlive = TimeSpan.FromSeconds(20);

    private TcpListener? listener;
    private CancellationTokenSource? cancel;

    public string? Error { get; private set; }

    public void Start()
    {
        Stop();
        try
        {
            listener = new TcpListener(IPAddress.Any, config.Port);
            listener.Start();
            Error = null;
        }
        catch (SocketException e)
        {
            listener = null;
            Error = e.Message;
            return;
        }

        cancel = new CancellationTokenSource();
        _ = AcceptLoop(listener, cancel.Token);
    }

    public void Dispose() => Stop();

    private void Stop()
    {
        cancel?.Cancel();
        cancel = null;
        listener?.Stop();
        listener = null;
    }

    private async Task AcceptLoop(TcpListener server, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            TcpClient tcp;
            try
            {
                tcp = await server.AcceptTcpClientAsync(token);
            }
            catch (Exception e) when (e is OperationCanceledException or SocketException or ObjectDisposedException)
            {
                return;
            }
            _ = Serve(tcp, token);
        }
    }

    private async Task Serve(TcpClient tcp, CancellationToken token)
    {
        using var connection = tcp;
        try
        {
            var stream = tcp.GetStream();
            using var socket = WebSocket.CreateFromStream(stream, new WebSocketCreationOptions { IsServer = true, KeepAliveInterval = KeepAlive });
            byte[]? key;
            using (var timeout = CancellationTokenSource.CreateLinkedTokenSource(token))
            {
                timeout.CancelAfter(HandshakeTimeout);
                if (!await WebSocketUpgrade.Accept(stream, timeout.Token))
                    return;
                key = await SecureChannel.Authenticate(socket, config.Phones, timeout.Token);
            }
            if (key == null)
                return;

            using var sealer = new Sealer(key);
            var client = new PhoneClient(socket, sealer);
            feed.Join(client);
            try
            {
                await client.Run(token);
            }
            finally
            {
                feed.Leave(client);
            }
        }
        catch (Exception e) when (e is IOException or SocketException or WebSocketException or OperationCanceledException or ObjectDisposedException)
        {
            Services.Log.Debug($"Phone connection closed: {e.Message}");
        }
    }
}
