using System;
using System.Linq;
using Dalamud.Game.Chat;
using Dalamud.Game.Text;
using Dalamud.Game.Text.SeStringHandling;
using Dalamud.Game.Text.SeStringHandling.Payloads;

namespace PartyBell;

internal sealed class ChatRelay : IDisposable
{
    private readonly Configuration config;
    private readonly Feed feed;
    private readonly PartyTracker tracker;

    public ChatRelay(Configuration config, Feed feed, PartyTracker tracker)
    {
        this.config = config;
        this.feed = feed;
        this.tracker = tracker;
        Services.ChatGui.ChatMessage += OnChatMessage;
    }

    public void Dispose() => Services.ChatGui.ChatMessage -= OnChatMessage;

    private void OnChatMessage(IHandleableChatMessage message)
    {
        if (!config.SendChat || Channel(message.LogKind) is not { } channel)
            return;

        var name = SenderName(message.Sender);
        var self = name == Services.PlayerState.CharacterName;
        var member = tracker.Find(name) ?? (self ? PartyReader.Self() : null);
        feed.Publish(Messages.Chat(name, member, message.Message.TextValue, channel, self));
    }

    private static string? Channel(XivChatType type) => type switch
    {
        XivChatType.Party or XivChatType.CrossParty => ChatSender.Party,
        XivChatType.Alliance => ChatSender.Alliance,
        _ => null,
    };

    private static string SenderName(SeString sender) =>
        sender.Payloads.OfType<PlayerPayload>().FirstOrDefault()?.PlayerName
        ?? new string(sender.TextValue.Where(c => char.IsLetter(c) || c is ' ' or '\'' or '-').ToArray()).Trim();
}
