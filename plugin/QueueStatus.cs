using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Enums;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using Sheets = Lumina.Excel.Sheets;
using InfoType = FFXIVClientStructs.FFXIV.Client.Game.UI.QueueInfoState.QueueContentType;

namespace PartyBell;

internal sealed record QueueCount(string Role, int Found, int Needed);

internal sealed record QueueStatus(string State, string Job, IReadOnlyList<string> Duties, long Entered, long Ready, int Position, int Wait, IReadOnlyList<QueueCount> Counts)
{
    private const long Second = 1000;

    public static unsafe QueueStatus? Read()
    {
        var finder = ContentsFinder.Instance();
        if (finder == null)
            return null;
        ref var queue = ref finder->QueueInfo;
        var state = queue.QueueState;
        if (state is not (ContentsFinderQueueState.Pending or ContentsFinderQueueState.Queued or ContentsFinderQueueState.Ready or ContentsFinderQueueState.Accepted))
            return null;
        var popped = state is ContentsFinderQueueState.Ready or ContentsFinderQueueState.Accepted;
        var known = state != ContentsFinderQueueState.Pending;
        var info = queue.InfoState;
        return new QueueStatus(
            StateOf(state, info),
            JobName(queue.QueuedClassJobId),
            popped ? [DutyQueue.Name(queue.PoppedQueueEntry)] : Queued(ref queue),
            queue.EnteredQueueTimestamp * Second,
            popped ? queue.QueueReadyTimestamp * Second : 0,
            PositionOf(known, popped, info),
            known ? info.AverageWaitTime : 0,
            known && !popped ? CountsOf(info) : []);
    }

    private static int PositionOf(bool known, bool popped, QueueInfoState info) =>
        !known ? 0 : !popped && info.ContentType == InfoType.PositionAndWaitTime ? info.PositionInQueue : -1;

    private static string StateOf(ContentsFinderQueueState state, QueueInfoState info) => state switch
    {
        ContentsFinderQueueState.Ready => "ready",
        ContentsFinderQueueState.Accepted => "accepted",
        ContentsFinderQueueState.Queued when info.IsReservingServer => "reserving",
        ContentsFinderQueueState.Queued when info.ContentType is InfoType.THDAndWaitTime or InfoType.PlayersAndWaitTime => "forming",
        _ => "waiting",
    };

    private static List<string> Queued(ref ContentsFinderQueueInfo queue)
    {
        var duties = new List<string>();
        foreach (var entry in queue.QueuedEntries)
            if (entry.ContentType != ContentsType.None)
                duties.Add(DutyQueue.Name(entry));
        if (duties.Count == 0 && queue.QueuedContentRouletteId != 0)
            duties.Add(DutyQueue.Name(new ContentsId { ContentType = ContentsType.Roulette, Id = queue.QueuedContentRouletteId }));
        return duties;
    }

    private static List<QueueCount> CountsOf(QueueInfoState info)
    {
        List<QueueCount> counts = info.ContentType switch
        {
            InfoType.THDAndWaitTime => [new("tank", info.TanksFound, info.TanksNeeded), new("healer", info.HealersFound, info.HealersNeeded), new("dps", info.DPSFound, info.DPSNeeded)],
            InfoType.PlayersAndWaitTime => [new("players", info.PlayersFound, info.PlayersNeeded)],
            _ => [],
        };
        return counts.Where(c => c.Needed > 0).ToList();
    }

    private static string JobName(uint id) =>
        DutyQueue.Capitalised(Services.DataManager.GetExcelSheet<Sheets.ClassJob>().GetRowOrDefault(id)?.Name.ExtractText()) ?? "";
}
