using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneServer(Feed feed, Configuration config, ChatSender chat, ReadyCheck readyCheck, Inviter inviter, PartyFinder finder, DutyQueue queue) : IDisposable
{
    private const int MaxHandshakes = 8;
    private static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan KeepAlive = TimeSpan.FromSeconds(20);
    private static readonly TimeSpan GoodbyeTimeout = TimeSpan.FromSeconds(1);
    private static readonly string Version = typeof(PhoneServer).Assembly.GetName().Version?.ToString(3) ?? "";

    private readonly SemaphoreSlim handshakes = new(MaxHandshakes);
    private TcpListener? listener;
    private CancellationTokenSource? cancel;

    public string? Error { get; private set; }

    public bool Running => listener != null;

    public PairedPhone? Pending { get; set; }

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

    public void Stop()
    {
        if (cancel != null)
            Task.WhenAny(feed.Goodbye(), Task.Delay(GoodbyeTimeout)).Wait();
        cancel?.Cancel();
        cancel = null;
        listener?.Stop();
        listener = null;
        Error = null;
    }

    private void Paired(PairedPhone phone)
    {
        if (Pending != phone)
            return;
        Pending = null;
        config.AddPhone(phone);
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
            if (tcp.Client.RemoteEndPoint is not IPEndPoint { Address: var address } || !Network.IsLocal(address))
                return;
            var stream = tcp.GetStream();
            using var socket = WebSocket.CreateFromStream(stream, new WebSocketCreationOptions { IsServer = true, KeepAliveInterval = KeepAlive });
            if (!handshakes.Wait(0))
                return;
            var pending = Pending;
            IReadOnlyList<PairedPhone> phones = pending == null ? config.Phones : [.. config.Phones, pending];
            Session? session;
            try
            {
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
                timeout.CancelAfter(HandshakeTimeout);
                session = await WebSocketUpgrade.Accept(stream, timeout.Token)
                    ? await SecureChannel.Authenticate(socket, phones, timeout.Token)
                    : null;
            }
            finally
            {
                handshakes.Release();
            }
            if (session == null)
                return;
            var phone = session.Phone;
            if (phone == pending)
                _ = Services.Framework.RunOnFrameworkThread(() => Paired(phone));

            using var sealer = new Sealer(session);
            var client = new PhoneClient(socket, sealer, chat, readyCheck, inviter, finder, queue);
            feed.Join(client, phone.PairedAt);
            client.Send(Messages.Plugin(Version));
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
