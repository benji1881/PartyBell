using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Dalamud.Game.Gui.PartyFinder.Types;
using Dalamud.Hooking;
using FFXIVClientStructs.FFXIV.Client.Game.Network;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using FFXIVClientStructs.FFXIV.Client.UI.Info;

namespace PartyBell;

// Collects Party Finder listings for the phone and hides listings from blocked players.
internal sealed unsafe class PartyFinder : IDisposable
{
    private const byte DataCenter = 0;
    private const string Window = "LookingForGroup";
    private static readonly TimeSpan Quiet = TimeSpan.FromMilliseconds(750);
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(5);
    private static readonly TimeSpan NoReply = TimeSpan.FromSeconds(2);

    private readonly Feed feed;
    private readonly Configuration config;
    private readonly ListingWindow window = new();
    private readonly Recruiter recruiter;
    private readonly object gate = new();
    private readonly Dictionary<string, Listing> listings = [];
    private readonly HashSet<ulong> blocked = [];
    private readonly Hook<InfoProxyCrossRealm.Delegates.ReceiveListing>? receiveHook;
    private int segment = -1;
    private int category;
    private bool dirty;
    private bool complete;
    private bool emptySent;
    private bool answered;
    private bool retried;
    private DateTime changedAt;
    private DateTime? requestedAt;

    public PartyFinder(Feed feed, Configuration config, PartyTracker tracker)
    {
        this.feed = feed;
        this.config = config;
        recruiter = new Recruiter(feed, config, tracker);
        Services.PartyFinderGui.ReceiveListing += OnListing;
        try
        {
            receiveHook = Services.GameInteropProvider.HookFromAddress<InfoProxyCrossRealm.Delegates.ReceiveListing>(InfoProxyCrossRealm.MemberFunctionPointers.ReceiveListing, OnReceive);
            receiveHook.Enable();
        }
        catch (Exception e)
        {
            Services.Log.Error(e, "Blocked players can't be hidden.");
        }
    }

    public void Dispose()
    {
        Services.PartyFinderGui.ReceiveListing -= OnListing;
        receiveHook?.Dispose();
    }

    public Task<string?> Request(Finder finder) => Services.Framework.RunOnFrameworkThread(() => Send(finder.Category));

    public Task<string?> Open(OpenListing open) => Services.Framework.RunOnFrameworkThread(() => window.Open(open.Id));

    public Task<string?> Join(JoinListing join) => PhoneGate.Run(config.AllowPhoneJoin, () => window.Join(join.Id));

    public Task<string?> Recruit(Recruit recruit) => recruiter.Start(recruit);

    public void Centre(DateTime now) => window.Centre(now);

    public void Update(DateTime now)
    {
        window.Step(now);
        recruiter.Step(now);
        recruiter.Update();
        lock (gate)
        {
            if (dirty && (complete || now - changedAt >= Quiet))
            {
                dirty = false;
                complete = false;
                requestedAt = null;
                if (category != 0)
                    feed.SetListings(category, Messages.Listings(category, listings.Values.ToList()));
            }
            else if (requestedAt is { } at && listings.Count == 0)
            {
                if (!emptySent && now - at >= NoReply)
                {
                    if (!answered && !retried && GameState.FinderBlocked() == null && !WindowOpen() && Ask((byte)category))
                    {
                        retried = true;
                        requestedAt = now;
                        return;
                    }
                    emptySent = true;
                    feed.SetListings(category, Messages.Listings(category, []));
                }
                if (now - at >= Timeout)
                    requestedAt = null;
            }
        }
    }

    public void Reset()
    {
        window.Reset();
        recruiter.Reset();
        answered = false;
        Clear();
    }

    private void Clear()
    {
        lock (gate)
        {
            listings.Clear();
            blocked.Clear();
            segment = -1;
            dirty = false;
            complete = false;
            emptySent = false;
            retried = false;
            requestedAt = null;
        }
    }

    private string? Send(byte wanted)
    {
        if (wanted is < 1 or > ListingMap.Other)
            return Reason.Invalid;
        if (GameState.FinderBlocked() is { } reason)
            return reason;
        if (WindowOpen())
            return Reason.WindowOpen;
        if (!Ask(wanted))
            return Reason.Refused;
        lock (gate)
        {
            Clear();
            category = wanted;
            requestedAt = DateTime.UtcNow;
        }
        return null;
    }

    private static bool Ask(byte wanted)
    {
        var agent = AgentLookingForGroup.Instance();
        if (agent == null)
            return false;
        agent->SearchAreaTab = DataCenter;
        agent->CategoryTab = wanted;
        return agent->RequestCategoryListings(wanted);
    }

    private static bool WindowOpen() => Services.GameGui.GetAddonByName(Window, 1).Address != IntPtr.Zero;

    private void OnListing(IPartyFinderListing listing, IPartyFinderListingEventArgs args)
    {
        var item = ListingMap.From(listing, Gear.ItemLevel());
        var hidden = !item.Own && ListingMap.DutyLocked(listing);
        if (!item.Own && IsBlocked(listing.Id))
            item = item with { Blocked = true };
        var now = DateTime.UtcNow;
        lock (gate)
        {
            if (segment == -1 || (segment == 0 && !dirty) || (args.BatchNumber == 1 && segment != 1) || now - changedAt >= Quiet)
            {
                listings.Clear();
                category = requestedAt != null ? category : Tab() ?? 0;
            }
            answered = true;
            segment = args.BatchNumber;
            complete = segment == 0;
            if (!hidden)
                listings[item.Id] = item;
            dirty = true;
            changedAt = now;
        }
    }

    private void OnReceive(InfoProxyCrossRealm* proxy, ServerIpcSegment<CrossRealmListingSegmentPacket>* packet)
    {
        try
        {
            var blacklist = InfoProxyBlacklist.Instance();
            lock (gate)
            {
                foreach (ref var entry in packet->Payload.Entries)
                {
                    if (entry.ListingId == 0)
                        continue;
                    if (blacklist != null && blacklist->GetBlockResultType(entry.AccountId, entry.ContentId) != InfoProxyBlacklist.BlockResultType.NotBlocked)
                        blocked.Add(entry.ListingId);
                    else
                        blocked.Remove(entry.ListingId);
                }
            }
        }
        catch (Exception e)
        {
            Services.Log.Error(e, "Couldn't check blocked players.");
        }
        receiveHook!.Original(proxy, packet);
    }

    private bool IsBlocked(ulong id)
    {
        lock (gate)
            return blocked.Contains(id);
    }

    private static int? Tab()
    {
        var agent = AgentLookingForGroup.Instance();
        return agent != null && agent->CategoryTab is >= 1 and <= ListingMap.Other ? agent->CategoryTab : null;
    }
}
