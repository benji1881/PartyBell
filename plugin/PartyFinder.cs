using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using System.Threading.Tasks;
using Dalamud.Game.ClientState.Conditions;
using Dalamud.Game.Gui.PartyFinder.Types;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed unsafe class PartyFinder : IDisposable
{
    private const byte Other = 16;
    private const byte DataCenter = 0;
    private const string Window = "LookingForGroup";
    private static readonly TimeSpan Quiet = TimeSpan.FromMilliseconds(750);
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(5);

    private readonly Feed feed;
    private readonly object gate = new();
    private readonly Dictionary<string, Listing> listings = [];
    private int segment = -1;
    private int category;
    private bool dirty;
    private bool complete;
    private DateTime changedAt;
    private DateTime? requestedAt;

    public PartyFinder(Feed feed)
    {
        this.feed = feed;
        Services.PartyFinderGui.ReceiveListing += OnListing;
    }

    public void Dispose() => Services.PartyFinderGui.ReceiveListing -= OnListing;

    public Task<string?> Request(Finder finder) => Services.Framework.RunOnFrameworkThread(() => Send(finder.Category));

    public Task<string?> Open(OpenListing open) => Services.Framework.RunOnFrameworkThread(() => Show(open.Id));

    public void Update(DateTime now)
    {
        lock (gate)
        {
            if (dirty && (complete || now - changedAt >= Quiet))
            {
                dirty = false;
                complete = false;
                requestedAt = null;
                feed.SetListings(Messages.Listings(category, listings.Values.ToList()));
            }
            else if (requestedAt is { } at && listings.Count == 0 && now - at >= Timeout)
            {
                requestedAt = null;
                feed.SetListings(Messages.Listings(category, []));
            }
        }
    }

    public void Reset()
    {
        lock (gate)
        {
            listings.Clear();
            segment = -1;
            dirty = false;
            complete = false;
            requestedAt = null;
        }
    }

    private string? Send(byte wanted)
    {
        if (wanted is < 1 or > Other)
            return "invalid";
        if (Blocked() is { } reason)
            return reason;
        if (Services.GameGui.GetAddonByName(Window, 1).Address != IntPtr.Zero)
            return "window open";
        var agent = AgentLookingForGroup.Instance();
        if (agent == null)
            return "refused";
        agent->SearchAreaTab = DataCenter;
        if (!agent->RequestCategoryListings(wanted))
            return "refused";
        lock (gate)
        {
            Reset();
            category = wanted;
            requestedAt = DateTime.UtcNow;
        }
        return null;
    }

    private string? Show(ulong id)
    {
        if (Blocked() is { } reason)
            return reason;
        var agent = AgentLookingForGroup.Instance();
        return agent != null && agent->OpenListing(id) ? null : "refused";
    }

    private static string? Blocked()
    {
        if (!Services.PlayerState.IsLoaded || Services.Condition[ConditionFlag.BetweenAreas] || Services.Condition[ConditionFlag.BetweenAreas51])
            return "not ready";
        if (Services.Condition[ConditionFlag.BoundByDuty] || Services.Condition[ConditionFlag.BoundByDuty56] || Services.Condition[ConditionFlag.BoundByDuty95])
            return "in duty";
        return null;
    }

    private void OnListing(IPartyFinderListing listing, IPartyFinderListingEventArgs args)
    {
        var item = Map(listing);
        var now = DateTime.UtcNow;
        lock (gate)
        {
            if (args.BatchNumber == 1 || segment == 0 || now - changedAt >= Quiet)
                listings.Clear();
            segment = args.BatchNumber;
            complete = segment == 0;
            listings[item.Id] = item;
            category = item.Category;
            dirty = true;
            changedAt = now;
        }
    }

    private static Listing Map(IPartyFinderListing listing)
    {
        var job = Services.PlayerState.ClassJob.RowId;
        var present = listing.RawJobsPresent.ToList();
        var jobs = Services.DataManager.GetExcelSheet<Sheets.ClassJob>();
        var slots = new List<Slot>();
        var fits = false;
        var index = 0;
        foreach (var slot in listing.Slots.Take(listing.SlotsAvailable))
        {
            var filled = index < present.Count ? present[index] : (byte)0;
            index++;
            if (filled != 0)
            {
                var member = jobs.GetRowOrDefault(filled);
                slots.Add(new Slot(member?.Abbreviation.ExtractText() ?? "", Role(member?.Role ?? 0)));
                continue;
            }
            var accepted = slot.Accepting.Select(f => f.ClassJob(Services.DataManager)).ToList();
            fits |= accepted.Any(j => j?.RowId == job);
            var roles = accepted.Select(j => Role(j?.Role ?? 0)).Distinct().ToList();
            slots.Add(new Slot("", roles.Count == 1 ? roles[0] : 0));
        }

        return new Listing(
            listing.Id.ToString(),
            DutyName(listing),
            CategoryOf(listing.Category),
            listing.Description.TextValue,
            listing.Name.TextValue,
            listing.HomeWorld.ValueNullable?.Name.ExtractText() ?? "",
            listing.SecondsRemaining,
            listing.MinimumItemLevel,
            slots,
            fits,
            listing.ContentId == Services.PlayerState.ContentId,
            listing[SearchAreaFlags.Private],
            listing[ObjectiveFlags.Practice],
            listing[ObjectiveFlags.Loot],
            listing[ObjectiveFlags.DutyCompletion],
            listing[ConditionFlags.DutyComplete],
            listing.BeginnersWelcome,
            listing[SearchAreaFlags.DataCenter]);
    }

    private static int Role(byte role) => role == 3 ? 2 : role;

    private static int CategoryOf(DutyCategory category) => category == DutyCategory.None ? Other : BitOperations.Log2((uint)category);

    private static string DutyName(IPartyFinderListing listing) => DutyQueue.Capitalised(listing.DutyType switch
    {
        DutyType.Roulette => Services.DataManager.GetExcelSheet<Sheets.ContentRoulette>().GetRowOrDefault(listing.RawDuty)?.Name.ExtractText(),
        DutyType.Normal => listing.Duty.ValueNullable?.Name.ExtractText(),
        _ => null,
    }) ?? "";
}
