using System;
using System.Linq;
using Dalamud.Game.Chat;
using Dalamud.Game.Text;
using Dalamud.Game.Text.SeStringHandling;
using Dalamud.Game.Text.SeStringHandling.Payloads;

namespace PartyBell;

internal sealed class ChatRelay : IDisposable
{
    private static readonly string CrossWorld = SeIconChar.CrossWorld.ToIconString();

    private readonly Configuration config;
    private readonly Feed feed;
    private readonly PartyTracker tracker;
    private readonly ChatSender chat;

    public ChatRelay(Configuration config, Feed feed, PartyTracker tracker, ChatSender chat)
    {
        this.config = config;
        this.feed = feed;
        this.tracker = tracker;
        this.chat = chat;
        Services.ChatGui.ChatMessage += OnChatMessage;
        Services.ClientState.Logout += OnLogout;
    }

    public void Dispose()
    {
        Services.ChatGui.ChatMessage -= OnChatMessage;
        Services.ClientState.Logout -= OnLogout;
    }

    private void OnLogout(int type, int code) => chat.Received("");

    private void OnChatMessage(IHandleableChatMessage message)
    {
        if (message.LogKind == XivChatType.TellIncoming)
            chat.Received(TellKey(Sender(message.Sender)));
        else if (message.LogKind == XivChatType.TellOutgoing)
            chat.Delivered(message.Message.TextValue);
        if (Channel(message.LogKind) is not { } channel || !Enabled(channel))
            return;

        var text = message.Message.TextValue;
        var sender = Sender(message.Sender);
        if (channel == ChatSender.Tell)
        {
            var outgoing = message.LogKind == XivChatType.TellOutgoing;
            var name = outgoing ? Services.PlayerState.CharacterName : sender.Name;
            var member = outgoing ? PartyReader.Self() : tracker.Find(name);
            feed.Publish(Messages.Chat(name, member, text, channel, outgoing, TellKey(sender)), true);
            return;
        }

        var self = sender.Name == Services.PlayerState.CharacterName;
        var known = tracker.Find(sender.Name) ?? (self ? PartyReader.Self() : null);
        feed.Publish(Messages.Chat(sender.Name, known, text, channel, self, ""), channel == ChatSender.FreeCompany);
    }

    private string TellKey((string Name, string Key) sender) =>
        tracker.Find(sender.Name) is { World.Length: > 0 } peer ? peer.Key : sender.Key;

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
            var world = player.World.ValueNullable?.Name.ExtractText() is { Length: > 0 } listed ? listed : CurrentWorld;
            return (player.PlayerName, $"{player.PlayerName}@{world}");
        }
        var text = string.Concat(sender.Payloads.Select(p => p is IconPayload { Icon: BitmapFontIcon.CrossWorld } ? CrossWorld : (p as ITextProvider)?.Text));
        var split = text.IndexOf(CrossWorld, StringComparison.Ordinal);
        var name = Letters(split < 0 ? text : text[..split]);
        var home = split < 0 ? CurrentWorld : Letters(text[(split + CrossWorld.Length)..]);
        return (name, $"{name}@{home}");
    }

    private static string Letters(string text) =>
        new string(text.Where(c => char.IsLetter(c) || c is ' ' or '\'' or '-').ToArray()).Trim();

    private static string CurrentWorld => Services.PlayerState.CurrentWorld.ValueNullable?.Name.ExtractText() ?? "";
}
