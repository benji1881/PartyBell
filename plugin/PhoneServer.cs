using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneServer(Feed feed, Configuration config, ChatSender chat, ReadyCheck readyCheck, Inviter inviter) : IDisposable
{
    private static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan KeepAlive = TimeSpan.FromSeconds(20);
    private static readonly TimeSpan GoodbyeTimeout = TimeSpan.FromSeconds(1);

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
        if (cancel != null)
            Task.WhenAny(feed.Goodbye(), Task.Delay(GoodbyeTimeout)).Wait();
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
            Session? session;
            using (var timeout = CancellationTokenSource.CreateLinkedTokenSource(token))
            {
                timeout.CancelAfter(HandshakeTimeout);
                if (!await WebSocketUpgrade.Accept(stream, timeout.Token))
                    return;
                session = await SecureChannel.Authenticate(socket, config.Phones, timeout.Token);
            }
            if (session == null)
                return;

            using var sealer = new Sealer(session);
            var client = new PhoneClient(socket, sealer, chat, readyCheck, inviter);
            feed.Join(client);
            if (session.Icons != JobIcons.Version)
                client.Send(JobIcons.Message);
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
