using System;
using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;

namespace PartyBell;

// Opens a listing in the game for the phone, centres it, and presses Join when asked to.
internal sealed unsafe class ListingWindow
{
    private const string Detail = "LookingForGroupDetail";
    private const uint JoinButton = 109;
    private const int Yes = 0;
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(5);

    private TaskCompletionSource<string?>? joining;
    private bool joinPressed;
    private DateTime joinBy;
    private DateTime? centreBy;

    public string? Open(ulong id)
    {
        if (GameState.FinderBlocked() is { } reason)
            return reason;
        var agent = AgentLookingForGroup.Instance();
        if (agent == null || !agent->OpenListing(id))
            return Reason.Refused;
        centreBy = DateTime.UtcNow + Timeout;
        return null;
    }

    public Task<string?> Join(ulong id)
    {
        if (joining != null)
            return Task.FromResult<string?>(Reason.Busy);
        if (Open(id) is { } reason)
            return Task.FromResult<string?>(reason);
        joining = new TaskCompletionSource<string?>();
        joinPressed = false;
        joinBy = DateTime.UtcNow + Timeout;
        return joining.Task;
    }

    public void Centre(DateTime now)
    {
        if (centreBy is not { } by)
            return;
        var detail = Addons.Ready(Detail);
        if (detail != null)
            Addons.Centre(detail);
        else if (now <= by)
            return;
        centreBy = null;
    }

    public void Step(DateTime now)
    {
        if (joining == null)
            return;
        if (now > joinBy)
        {
            Finish(Reason.Timeout);
            return;
        }
        if (!joinPressed)
        {
            var detail = Addons.Ready(Detail);
            var button = detail == null ? null : detail->GetComponentButtonById(JoinButton);
            if (button == null || !button->IsEnabled)
                return;
            detail->FireCallbackInt(Yes);
            joinPressed = true;
            return;
        }
        var confirm = Addons.Ready(Addons.YesNo);
        if (confirm != null)
        {
            confirm->FireCallbackInt(Yes);
            Finish(null);
        }
    }

    public void Reset()
    {
        Finish(Reason.NotReady);
        centreBy = null;
    }

    private void Finish(string? reason)
    {
        var task = joining;
        joining = null;
        task?.TrySetResult(reason);
    }
}
