using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneClient(WebSocket socket, Sealer sealer, ChatSender chat, ReadyCheck readyCheck, Inviter inviter, PartyFinder finder, DutyQueue queue)
{
    private const int MaxMessageSize = 8192;

    private readonly Channel<string> outbox = Channel.CreateUnbounded<string>(new UnboundedChannelOptions { SingleReader = true });

    private Task sending = Task.CompletedTask;

    public void Send(string json) => outbox.Writer.TryWrite(json);

    public Task Goodbye()
    {
        outbox.Writer.TryWrite(Messages.Bye());
        outbox.Writer.TryComplete();
        return sending;
    }

    public async Task Run(CancellationToken token)
    {
        sending = SendLoop(token);
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
            if (socket.State == WebSocketState.Open)
                await socket.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, null, token);
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
                    _ = Said(say);
                else if (Answer.Parse(plain) is { } answer)
                    Send(Messages.Answered(answer.Ready, await readyCheck.Answer(answer.Ready)));
                else if (DutyAction.Parse(plain) is { } duty)
                    Send(Messages.Duty(duty.Action, await queue.Act(duty.Action)));
                else if (Invite.Parse(plain) is { } invite)
                    Send(Messages.Invited(invite.Id, await inviter.Send(invite)));
                else if (Finder.Parse(plain) is { } request)
                    Send(Messages.Finding(await finder.Request(request)));
                else if (OpenListing.Parse(plain) is { } open)
                    Send(Messages.Opened(await finder.Open(open)));
                else if (JoinListing.Parse(plain) is { } join)
                    Send(Messages.Joining(await finder.Join(join)));
                else if (RefreshFriends.Parse(plain) is not null)
                    Send(Messages.Refreshing(await FriendList.Refresh()));
            }
        }
        catch (Exception e) when (IsDisconnect(e))
        {
        }
    }

    private async Task Said(Say say) => Send(Messages.Said(say.Id, await chat.Send(say)));

    private static bool IsDisconnect(Exception e) => e is WebSocketException or IOException or OperationCanceledException;
}
