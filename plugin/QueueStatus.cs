using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Enums;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using Sheets = Lumina.Excel.Sheets;
using InfoType = FFXIVClientStructs.FFXIV.Client.Game.UI.QueueInfoState.QueueContentType;

namespace PartyBell;

internal sealed record QueueCount(string Role, int Found, int Needed);

internal sealed record QueueStatus(string State, string Job, IReadOnlyList<string> Duties, long Entered, long Ready, long Expires, int Position, int Wait, IReadOnlyList<QueueCount> Counts, bool Actions)
{
    private const long Second = 1000;
    private const long AcceptWindow = 45 * Second;
    private const byte Unknown = 255;
    private const byte PastLast = 254;
    private const int LastPosition = 50;

    public static unsafe QueueStatus? Read(bool actions)
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
        var ready = popped ? queue.QueueReadyTimestamp * Second : 0;
        return new QueueStatus(
            StateOf(state, info),
            JobName(queue.QueuedClassJobId),
            popped ? [DutyQueue.Name(queue.PoppedQueueEntry)] : Queued(ref queue),
            queue.EnteredQueueTimestamp * Second,
            ready,
            popped ? ready + AcceptWindow : 0,
            PositionOf(known, popped, info),
            WaitOf(known, info),
            known && !popped ? CountsOf(info) : [],
            actions);
    }

    private static int PositionOf(bool known, bool popped, QueueInfoState info) =>
        !known ? 0 : popped || info.ContentType != InfoType.PositionAndWaitTime ? -1 : info.PositionInQueue switch
        {
            Unknown => 0,
            PastLast => LastPosition + 1,
            var position => position,
        };

    private static int WaitOf(bool known, QueueInfoState info) =>
        known && info.ContentType <= InfoType.WaitTime && info.AverageWaitTime != Unknown ? info.AverageWaitTime : -1;

    private static string StateOf(ContentsFinderQueueState state, QueueInfoState info) => state switch
    {
        ContentsFinderQueueState.Ready => "ready",
        ContentsFinderQueueState.Accepted => "accepted",
        ContentsFinderQueueState.Queued when info.ContentType != InfoType.PositionAndWaitTime => info.IsReservingServer ? "reserving" : "forming",
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
        GameText.Capitalised(Services.DataManager.GetExcelSheet<Sheets.ClassJob>().GetRowOrDefault(id)?.Name.ExtractText()) ?? "";
}
