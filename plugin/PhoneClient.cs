using System;
using System.Collections.Generic;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class PhoneClient(PairedPhone phone, WebSocket socket, Sealer sealer, ChatSender chat, ReadyCheck readyCheck, Inviter inviter, PartyFinder finder, DutyQueue queue)
{
    private const int MaxMessageSize = 8192;
    private const int MaxQueued = 1000;
    private const string RefreshFriends = "friendsrefresh";

    private readonly Channel<string> outbox = Channel.CreateBounded<string>(new BoundedChannelOptions(MaxQueued) { SingleReader = true });

    private Task sending = Task.CompletedTask;

    public PairedPhone Phone => phone;

    // A phone that stops reading would make the queue grow forever, so drop it instead.
    public void Send(string json)
    {
        if (!outbox.Writer.TryWrite(json) && outbox.Reader.Count >= MaxQueued)
            Drop();
    }

    public void Drop() => socket.Abort();

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

    // Requests are answered in the background so a receive is always waiting, which the keep-alive needs.
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
                if (Route(plain) is { } reply)
                    _ = Reply(reply);
            }
        }
        catch (Exception e) when (IsDisconnect(e))
        {
        }
    }

    private Task<string>? Route(byte[] plain)
    {
        try
        {
            using var document = JsonDocument.Parse(plain);
            var root = document.RootElement;
            return root.GetProperty("type").GetString() switch
            {
                Say.Type => Said(Say.Read(root)),
                Answer.Type => Answered(Answer.Read(root)),
                DutyAction.Type => Dutied(DutyAction.Read(root)),
                Invite.Type => Invited(Invite.Read(root)),
                Finder.Type => Found(Finder.Read(root)),
                OpenListing.Type => Opened(OpenListing.Read(root)),
                JoinListing.Type => Joined(JoinListing.Read(root)),
                RefreshFriends => Refreshed(),
                ItemIconRequest.Type => ItemIcons(ItemIconRequest.Read(root)),
                _ => null,
            };
        }
        catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException or FormatException or OverflowException)
        {
            return null;
        }
    }

    private async Task Reply(Task<string> reply)
    {
        try
        {
            Send(await reply);
        }
        catch (Exception e)
        {
            Services.Log.Error(e, "A phone request failed.");
        }
    }

    private async Task<string> Said(Say say)
    {
        var (reason, error) = await chat.Send(say);
        return Messages.Said(say.Id, reason, error);
    }

    private async Task<string> Answered(Answer answer) => Messages.Answered(answer.Ready, await readyCheck.Answer(answer.Ready));

    private async Task<string> Dutied(DutyAction duty) => Messages.Duty(duty.Action, await queue.Act(duty.Action));

    private async Task<string> Invited(Invite invite) => Messages.Invited(invite.Id, await inviter.Send(invite));

    private async Task<string> Found(Finder request) => Messages.Finding(await finder.Request(request));

    private async Task<string> Opened(OpenListing open) => Messages.Opened(await finder.Open(open));

    private async Task<string> Joined(JoinListing join) => Messages.Joining(await finder.Join(join));

    // Game files are read on the framework thread, like the rest of the game data.
    private static Task<string> ItemIcons(ItemIconRequest request) =>
        Services.Framework.RunOnFrameworkThread(() => Messages.ItemIcons(JobIcons.Items(request.Ids)));

    private static async Task<string> Refreshed() => Messages.Refreshing(await FriendList.Refresh());

    private static bool IsDisconnect(Exception e) => e is WebSocketException or IOException or OperationCanceledException or ObjectDisposedException;
}
