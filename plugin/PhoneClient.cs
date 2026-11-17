using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneClient(WebSocket socket, Sealer sealer)
{
    private readonly Channel<string> outbox = Channel.CreateUnbounded<string>(new UnboundedChannelOptions { SingleReader = true });

    public void Send(string json) => outbox.Writer.TryWrite(json);

    public async Task Run(CancellationToken token)
    {
        var sending = SendLoop(token);
        await ReceiveLoop(token);
        outbox.Writer.TryComplete();
        await sending;
        if (socket.State == WebSocketState.CloseReceived)
        {
            try
            {
                await socket.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, null, token);
            }
            catch (Exception e) when (IsDisconnect(e))
            {
            }
        }
    }

    private async Task SendLoop(CancellationToken token)
    {
        try
        {
            await foreach (var json in outbox.Reader.ReadAllAsync(token))
                await socket.SendAsync(sealer.Seal(Encoding.UTF8.GetBytes(json)), WebSocketMessageType.Binary, true, token);
        }
        catch (Exception e) when (IsDisconnect(e))
        {
        }
    }

    private async Task ReceiveLoop(CancellationToken token)
    {
        var buffer = new byte[1024];
        try
        {
            while (socket.State == WebSocketState.Open)
            {
                var result = await socket.ReceiveAsync(buffer.AsMemory(), token);
                if (result.MessageType == WebSocketMessageType.Close)
                    return;
            }
        }
        catch (Exception e) when (IsDisconnect(e))
        {
        }
    }

    private static bool IsDisconnect(Exception e) => e is WebSocketException or IOException or OperationCanceledException;
}
