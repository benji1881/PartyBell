using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.Enums;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using FFXIVClientStructs.FFXIV.Client.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed unsafe class DutyQueue(Feed feed, Configuration config)
{
    private const string Fallback = "Duty";
    private const string Confirm = "ContentsFinderConfirm";

    private bool popped;

    public static bool Matched
    {
        get
        {
            var finder = ContentsFinder.Instance();
            return finder != null && finder->QueueInfo.QueueState is ContentsFinderQueueState.Ready or ContentsFinderQueueState.Accepted or ContentsFinderQueueState.InContent;
        }
    }

    public void Update()
    {
        var finder = ContentsFinder.Instance();
        var ready = finder != null && finder->QueueInfo.QueueState == ContentsFinderQueueState.Ready;
        feed.SetQueue(Messages.QueueStatus(QueueStatus.Read(config.AllowPhoneDuty)));
        if (ready == popped)
            return;
        popped = ready;
        if (ready)
            feed.Publish(Messages.Queue(Name(finder->QueueInfo.PoppedQueueEntry)));
        else
            feed.Broadcast(Messages.Dismiss(AlertKind.Queue));
    }

    public Task<string?> Act(string action) => PhoneGate.Run(config.AllowPhoneDuty, () => Run(action));

    private static string? Run(string action)
    {
        var finder = ContentsFinder.Instance();
        if (finder == null)
            return Reason.NotQueued;
        var state = finder->QueueInfo.QueueState;
        if (action == DutyAction.Leave)
        {
            if (state is not (ContentsFinderQueueState.Pending or ContentsFinderQueueState.Queued))
                return Reason.NotQueued;
            finder->QueueInfo.CancelQueue();
            return null;
        }
        if (state != ContentsFinderQueueState.Ready)
            return Reason.NotReady;
        var addon = (AddonContentsFinderConfirm*)Addons.Ready(Confirm);
        if (addon == null)
            return Reason.NoWindow;
        var button = action switch
        {
            DutyAction.Commence => addon->CommenceButton,
            DutyAction.Withdraw => addon->WithdrawButton,
            _ => null,
        };
        return button != null && Addons.Click(&addon->AtkUnitBase, button) ? null : Reason.Failed;
    }

    public void Reset()
    {
        feed.SetQueue(Messages.QueueStatus(null));
        if (!popped)
            return;
        popped = false;
        feed.Broadcast(Messages.Dismiss(AlertKind.Queue));
    }

    public static string Name(ContentsId duty)
    {
        var name = duty.ContentType switch
        {
            ContentsType.Roulette => Services.DataManager.GetExcelSheet<Sheets.ContentRoulette>().GetRowOrDefault(duty.Id)?.Name.ExtractText(),
            ContentsType.Regular => Services.DataManager.GetExcelSheet<Sheets.ContentFinderCondition>().GetRowOrDefault(duty.Id)?.Name.ExtractText(),
            _ => null,
        };
        return GameText.Capitalised(name) ?? Fallback;
    }
}
