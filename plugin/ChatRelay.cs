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
        if (Channel(message.LogKind) is not { } channel || !Enabled(channel))
            return;

        var text = message.Message.TextValue;
        var sender = Sender(message.Sender);
        if (channel == ChatSender.Tell)
        {
            var outgoing = message.LogKind == XivChatType.TellOutgoing;
            var name = outgoing ? Services.PlayerState.CharacterName : sender.Name;
            var member = outgoing ? PartyReader.Self() : tracker.Find(name);
            feed.Publish(Messages.Chat(name, member, text, channel, outgoing, sender.Key), true);
            return;
        }

        var self = sender.Name == Services.PlayerState.CharacterName;
        var known = tracker.Find(sender.Name) ?? (self ? PartyReader.Self() : null);
        feed.Publish(Messages.Chat(sender.Name, known, text, channel, self, ""), channel == ChatSender.FreeCompany);
    }

    private bool Enabled(string channel) => channel switch
    {
        ChatSender.Tell => config.SendTells,
        ChatSender.FreeCompany => config.SendFreeCompanyChat,
        _ => config.SendChat,
    };

    private static string? Channel(XivChatType type) => type switch
    {
        XivChatType.Party or XivChatType.CrossParty => ChatSender.Party,
        XivChatType.Alliance => ChatSender.Alliance,
        XivChatType.FreeCompany => ChatSender.FreeCompany,
        XivChatType.TellIncoming or XivChatType.TellOutgoing => ChatSender.Tell,
        _ => null,
    };

    private static (string Name, string Key) Sender(SeString sender)
    {
        if (sender.Payloads.OfType<PlayerPayload>().FirstOrDefault() is { } player)
        {
            var world = player.World.RowId != 0 ? player.World.Value.Name.ExtractText() : CurrentWorld;
            return (player.PlayerName, $"{player.PlayerName}@{world}");
        }
        var name = new string(sender.TextValue.Where(c => char.IsLetter(c) || c is ' ' or '\'' or '-').ToArray()).Trim();
        return (name, $"{name}@{CurrentWorld}");
    }

    private static string CurrentWorld => Services.PlayerState.CurrentWorld.Value.Name.ExtractText();
}
