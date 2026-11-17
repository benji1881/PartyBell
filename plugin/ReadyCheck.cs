using System;
using System.Threading.Tasks;
using Dalamud.Hooking;
using FFXIVClientStructs.FFXIV.Client.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using FFXIVClientStructs.FFXIV.Component.GUI;

namespace PartyBell;

internal sealed unsafe class ReadyCheck : IDisposable
{
    private const string Prompt = "SelectYesno";
    private const int PromptSlots = 4;
    private static readonly TimeSpan MaxDuration = TimeSpan.FromSeconds(35);

    private readonly Feed feed;
    private readonly Configuration config;
    private readonly Hook<AgentReadyCheck.Delegates.InitiateReadyCheck>? startHook;
    private readonly Hook<AgentReadyCheck.Delegates.EndReadyCheck>? endHook;
    private bool started;
    private bool running;
    private DateTime startedAt;
    private ReadyCheckStatus own;

    public ReadyCheck(Feed feed, Configuration config)
    {
        this.feed = feed;
        this.config = config;
        try
        {
            startHook = Services.GameInteropProvider.HookFromAddress<AgentReadyCheck.Delegates.InitiateReadyCheck>(AgentReadyCheck.MemberFunctionPointers.InitiateReadyCheck, OnStart);
            endHook = Services.GameInteropProvider.HookFromAddress<AgentReadyCheck.Delegates.EndReadyCheck>(AgentReadyCheck.MemberFunctionPointers.EndReadyCheck, OnEnd);
            startHook.Enable();
            endHook.Enable();
        }
        catch (Exception e)
        {
            Services.Log.Error(e, "Ready checks are not available.");
        }
    }

    public void Dispose()
    {
        startHook?.Dispose();
        endHook?.Dispose();
    }

    public void Update()
    {
        if (started)
        {
            started = false;
            running = true;
            startedAt = DateTime.UtcNow;
            own = OwnStatus();
            feed.Publish(Messages.ReadyCheck(own == ReadyCheckStatus.AwaitingResponse));
            return;
        }
        if (!running)
            return;
        if (DateTime.UtcNow - startedAt > MaxDuration)
        {
            Finish();
            return;
        }

        var status = OwnStatus();
        if (own == ReadyCheckStatus.AwaitingResponse && status is ReadyCheckStatus.Ready or ReadyCheckStatus.NotReady)
            feed.Broadcast(Messages.Answered(status == ReadyCheckStatus.Ready, null));
        own = status;
    }

    public void Reset()
    {
        if (!started && !running)
            return;
        started = false;
        running = false;
        feed.Broadcast(Messages.Dismiss("readycheck"));
    }

    public Task<string?> Answer(bool ready) => config.AllowPhoneReadyCheck
        ? Services.Framework.RunOnFrameworkThread(() => Click(ready))
        : Task.FromResult<string?>("disabled");

    private void OnStart(AgentReadyCheck* agent)
    {
        startHook!.Original(agent);
        started = true;
    }

    private void OnEnd(AgentReadyCheck* agent)
    {
        endHook!.Original(agent);
        started = false;
        if (running)
            Finish();
    }

    private void Finish()
    {
        running = false;
        var notReady = 0;
        foreach (var entry in AgentReadyCheck.Instance()->ReadyCheckEntries)
        {
            if (entry.ContentId != 0 && entry.Status != ReadyCheckStatus.Ready)
                notReady++;
        }
        feed.Publish(Messages.ReadyCheckDone(notReady));
    }

    private string? Click(bool ready)
    {
        if (!running)
            return "no ready check";
        if (OwnStatus() != ReadyCheckStatus.AwaitingResponse)
            return "answered";
        var prompt = FindPrompt();
        if (prompt == null)
            return "no prompt";

        var value = new AtkValue { Type = AtkValueType.Int, Int = ready ? 0 : 1 };
        prompt->FireCallback(1, &value, true);
        return null;
    }

    private static ReadyCheckStatus OwnStatus()
    {
        var self = Services.PlayerState.ContentId;
        foreach (var entry in AgentReadyCheck.Instance()->ReadyCheckEntries)
        {
            if (entry.ContentId == self)
                return entry.Status;
        }
        return ReadyCheckStatus.Unknown;
    }

    private static AtkUnitBase* FindPrompt()
    {
        var agent = AgentModule.Instance()->GetAgentByInternalId(AgentId.ReadyCheck);
        for (var i = 1; i <= PromptSlots; i++)
        {
            var addon = Services.GameGui.GetAddonByName<AtkUnitBase>(Prompt, i);
            if (addon == null || !addon->IsVisible)
                continue;
            var id = addon->ParentId == 0 ? addon->Id : addon->ParentId;
            if (RaptureAtkModule.Instance()->AddonCallbackMapping.TryGetValuePointer(id, out var entry) && entry->AgentInterface == agent)
                return addon;
        }
        return null;
    }
}
