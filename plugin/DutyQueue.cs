using FFXIVClientStructs.FFXIV.Client.Enums;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed unsafe class DutyQueue(Feed feed)
{
    private const string Fallback = "Duty";

    private bool popped;

    public void Update()
    {
        var finder = ContentsFinder.Instance();
        var ready = finder != null && finder->QueueInfo.QueueState == ContentsFinderQueueState.Ready;
        if (ready == popped)
            return;
        popped = ready;
        if (ready)
            feed.Publish(Messages.Queue(Name(finder->QueueInfo.PoppedQueueEntry)));
        else
            feed.Broadcast(Messages.Dismiss("queue"));
    }

    public void Reset()
    {
        if (!popped)
            return;
        popped = false;
        feed.Broadcast(Messages.Dismiss("queue"));
    }

    private static string Name(ContentsId duty)
    {
        var name = duty.ContentType switch
        {
            ContentsType.Roulette => Services.DataManager.GetExcelSheet<Sheets.ContentRoulette>().GetRowOrDefault(duty.Id)?.Name.ExtractText(),
            ContentsType.Regular => Services.DataManager.GetExcelSheet<Sheets.ContentFinderCondition>().GetRowOrDefault(duty.Id)?.Name.ExtractText(),
            _ => null,
        };
        return string.IsNullOrWhiteSpace(name) ? Fallback : char.ToUpperInvariant(name[0]) + name[1..];
    }
}
