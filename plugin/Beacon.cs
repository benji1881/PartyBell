using System;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

// Answers a paired phone on the home network that asks where this PC is, so it finds the PC after its address changes.
// The answer holds nothing: the phone uses the address it came from and still has to pass the handshake. Keep in step with Discovery.kt.
internal sealed class Beacon(Configuration config) : IDisposable
{
    private static readonly byte[] Ask = "PartyBell?"u8.ToArray();
    private static readonly byte[] Here = "PartyBell!"u8.ToArray();

    private UdpClient? udp;
    private CancellationTokenSource? cancel;

    public void Start(int port)
    {
        Stop();
        try
        {
            udp = new UdpClient(new IPEndPoint(IPAddress.Any, port));
        }
        catch (SocketException e)
        {
            Services.Log.Warning(e, "Phones can't look for this PC, so they have to pair again if its address changes.");
            return;
        }
        cancel = new CancellationTokenSource();
        _ = Listen(udp, cancel.Token);
    }

    public void Stop()
    {
        cancel?.Cancel();
        cancel = null;
        udp?.Dispose();
        udp = null;
    }

    public void Dispose() => Stop();

    private async Task Listen(UdpClient socket, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var probe = await socket.ReceiveAsync(token);
                if (Network.IsHome(probe.RemoteEndPoint.Address) && IsPaired(probe.Buffer))
                    await socket.SendAsync(Here, probe.RemoteEndPoint, token);
            }
            catch (Exception e) when (e is OperationCanceledException or ObjectDisposedException)
            {
                return;
            }
            catch (SocketException)
            {
                // Windows reports an earlier answer that couldn't be delivered here. Keep listening.
            }
        }
    }

    private bool IsPaired(byte[] probe)
    {
        if (!probe.AsSpan().StartsWith(Ask))
            return false;
        var id = Encoding.ASCII.GetString(probe, Ask.Length, probe.Length - Ask.Length);
        return config.Phones.Any(p => p.Id == id);
    }
}
