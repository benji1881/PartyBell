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

    private readonly Feed feed = new();
    private readonly PartyTracker tracker = new();
    private readonly List<PartyEvent> events = [];
    private readonly WindowSystem windows = new("PartyBell");
    private readonly PhoneServer server;
    private readonly ChatRelay chat;
    private readonly MainWindow window;
    private DateTime nextRead;

    public Plugin(IDalamudPluginInterface pluginInterface)
    {
        pluginInterface.Create<Services>();
        var config = pluginInterface.GetPluginConfig() as Configuration ?? new Configuration();
        config.Save();

        server = new PhoneServer(feed, config);
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
    }

    private void OnUpdate(IFramework framework)
    {
        var now = DateTime.UtcNow;
        if (now < nextRead)
            return;
        nextRead = now + ReadInterval;

        if (!Services.PlayerState.IsLoaded)
        {
            if (tracker.Reset())
                feed.SetParty(Messages.Party(PartySnapshot.Empty));
            return;
        }
        if (Services.Condition[ConditionFlag.BetweenAreas] || Services.Condition[ConditionFlag.BetweenAreas51])
            return;

        events.Clear();
        var selfKey = $"{Services.PlayerState.CharacterName}@{Services.PlayerState.HomeWorld.Value.Name.ExtractText()}";
        if (!tracker.Update(PartyReader.Read(), selfKey, now, events))
            return;

        feed.SetParty(Messages.Party(tracker.Current!));
        foreach (var e in events)
            feed.Publish(Messages.Event(e));
    }
}
