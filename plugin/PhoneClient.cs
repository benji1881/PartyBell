using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneClient(WebSocket socket, Sealer sealer, ChatSender chat, ReadyCheck readyCheck)
{
    private const int MaxMessageSize = 8192;

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
        var buffer = new byte[MaxMessageSize];
        try
        {
            while (socket.State == WebSocketState.Open)
            {
                var count = 0;
                ValueWebSocketReceiveResult result;
                do
                {
                    if (count == buffer.Length)
                    {
                        await socket.CloseOutputAsync(WebSocketCloseStatus.MessageTooBig, null, token);
                        return;
                    }
                    result = await socket.ReceiveAsync(buffer.AsMemory(count), token);
                    if (result.MessageType == WebSocketMessageType.Close)
                        return;
                    count += result.Count;
                }
                while (!result.EndOfMessage);

                var plain = result.MessageType == WebSocketMessageType.Binary ? sealer.Open(buffer.AsSpan(0, count)) : null;
                if (plain == null)
                {
                    await socket.CloseOutputAsync(WebSocketCloseStatus.PolicyViolation, null, token);
                    return;
                }
                if (Say.Parse(plain) is { } say)
                    Send(Messages.Said(say.Id, await chat.Send(say)));
                else if (Answer.Parse(plain) is { } answer)
                    Send(Messages.Answered(answer.Ready, await readyCheck.Answer(answer.Ready)));
            }
        }
        catch (Exception e) when (IsDisconnect(e))
        {
        }
    }

    private static bool IsDisconnect(Exception e) => e is WebSocketException or IOException or OperationCanceledException;
}
