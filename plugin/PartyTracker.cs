using System;
using System.Collections.Generic;
using System.Linq;

namespace PartyBell;

internal sealed class PartyTracker
{
    private static readonly TimeSpan SettleTime = TimeSpan.FromSeconds(1);

    private PartySnapshot? pending;
    private DateTime pendingSince;

    public PartySnapshot? Current { get; private set; }

    public bool Update(PartySnapshot snapshot, string selfKey, DateTime now, List<PartyEvent> events)
    {
        if (Current != null && snapshot.SameAs(Current))
        {
            pending = null;
            return false;
        }

        if (pending == null || !snapshot.SameAs(pending))
        {
            pending = snapshot;
            pendingSince = now;
            return false;
        }

        if (now - pendingSince < SettleTime)
            return false;

        if (Current != null)
            Compare(Current, snapshot, selfKey, events);
        Current = snapshot;
        pending = null;
        return true;
    }

    public bool Reset()
    {
        var had = Current != null;
        Current = null;
        pending = null;
        return had;
    }

    private static void Compare(PartySnapshot before, PartySnapshot after, string selfKey, List<PartyEvent> events)
    {
        if (before.Members.Count >= 2 && after.Members.Count <= 1)
        {
            events.Add(new PartyEvent("disbanded", null));
            return;
        }

        var beforeKeys = before.Members.Select(m => m.Key).ToHashSet();
        var afterKeys = after.Members.Select(m => m.Key).ToHashSet();
        events.AddRange(before.Members.Where(m => m.Key != selfKey && !afterKeys.Contains(m.Key)).Select(m => new PartyEvent("left", m)));
        events.AddRange(after.Members.Where(m => m.Key != selfKey && !beforeKeys.Contains(m.Key)).Select(m => new PartyEvent("joined", m)));

        if (after.IsFull && !before.IsFull)
            events.Add(new PartyEvent("full", null));
    }
}
