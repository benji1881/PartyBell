using System;
using System.Collections.Generic;
using Dalamud.Game.ClientState.Conditions;
using Dalamud.Game.Command;
using Dalamud.Interface.Windowing;
using Dalamud.Plugin;
using Dalamud.Plugin.Services;

namespace PartyBell;

public sealed class Plugin : IDalamudPlugin
{
    private const string Command = "/partybell";
    private static readonly TimeSpan ReadInterval = TimeSpan.FromMilliseconds(250);
    private static readonly TimeSpan ZoneGrace = TimeSpan.FromSeconds(8);
    private static readonly TimeSpan FriendInterval = TimeSpan.FromSeconds(2);

    private readonly Feed feed = new();
    private readonly PartyTracker tracker = new();
    private readonly List<PartyEvent> events = [];
    private readonly WindowSystem windows = new("PartyBell");
    private readonly PhoneServer server;
    private readonly ChatRelay chat;
    private readonly ReadyCheck readyCheck;
    private readonly DutyQueue queue;
    private readonly MainWindow window;
    private readonly Configuration config;
    private DateTime nextRead;
    private DateTime zonedAt;
    private DateTime nextFriends;

    public Plugin(IDalamudPluginInterface pluginInterface)
    {
        pluginInterface.Create<Services>();
        config = pluginInterface.GetPluginConfig() as Configuration ?? new Configuration();
        config.Save();

        readyCheck = new ReadyCheck(feed, config);
        queue = new DutyQueue(feed);
        server = new PhoneServer(feed, config, new ChatSender(config, tracker), readyCheck, new Inviter(config, tracker));
        chat = new ChatRelay(config, feed, tracker);
        window = new MainWindow(config, server, feed);
        windows.AddWindow(window);
        server.Start();

        pluginInterface.UiBuilder.Draw += windows.Draw;
        pluginInterface.UiBuilder.OpenMainUi += window.Toggle;
        pluginInterface.UiBuilder.OpenConfigUi += window.Toggle;
        Services.CommandManager.AddHandler(Command, new CommandInfo((_, _) => window.Toggle()) { HelpMessage = "Open PartyBell." });
        Services.Framework.Update += OnUpdate;
    }

    public void Dispose()
    {
        Services.Framework.Update -= OnUpdate;
        Services.CommandManager.RemoveHandler(Command);
        Services.PluginInterface.UiBuilder.Draw -= windows.Draw;
        Services.PluginInterface.UiBuilder.OpenMainUi -= window.Toggle;
        Services.PluginInterface.UiBuilder.OpenConfigUi -= window.Toggle;
        windows.RemoveAllWindows();
        chat.Dispose();
        server.Dispose();
        readyCheck.Dispose();
    }

    private void OnUpdate(IFramework framework)
    {
        readyCheck.Update();
        var now = DateTime.UtcNow;
        if (now < nextRead)
            return;
        nextRead = now + ReadInterval;

        if (!Services.PlayerState.IsLoaded)
        {
            zonedAt = now;
            if (tracker.Reset())
                feed.SetParty(Messages.Party(PartySnapshot.Empty));
            feed.SetCompany(Messages.Company(false));
            readyCheck.Reset();
            queue.Reset();
            return;
        }
        if (Services.Condition[ConditionFlag.BetweenAreas] || Services.Condition[ConditionFlag.BetweenAreas51])
        {
            zonedAt = now;
            return;
        }

        queue.Update();
        if (now >= nextFriends)
        {
            nextFriends = now + FriendInterval;
            feed.SetFriends(Messages.Friends(config.SendFriends && feed.Count > 0 ? FriendList.Read() : null));
            feed.SetCompany(Messages.Company(Company.IsMember));
        }
        events.Clear();
        var selfKey = $"{Services.PlayerState.CharacterName}@{Services.PlayerState.HomeWorld.Value.Name.ExtractText()}";
        var before = tracker.Current;
        if (!tracker.Update(PartyReader.Read(), selfKey, now, now - zonedAt < ZoneGrace, events))
            return;

        if (before is not { Members.Count: > 1 } && tracker.Current!.Members.Count > 1)
            feed.ClearBefore(tracker.ChangedAt);

        feed.SetParty(Messages.Party(tracker.Current!));
        foreach (var e in events)
            feed.Publish(Messages.Event(e));
    }
}
