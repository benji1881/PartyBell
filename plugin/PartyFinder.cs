using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using System.Threading.Tasks;
using Dalamud.Game.ClientState.Conditions;
using Dalamud.Game.Gui.PartyFinder.Types;
using Dalamud.Hooking;
using FFXIVClientStructs.FFXIV.Client.Game;
using FFXIVClientStructs.FFXIV.Client.Game.Network;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using FFXIVClientStructs.FFXIV.Client.UI.Info;
using FFXIVClientStructs.FFXIV.Component.GUI;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed unsafe class PartyFinder : IDisposable
{
    private const byte Other = 16;
    private const byte DataCenter = 0;
    private const string Window = "LookingForGroup";
    private const string Detail = "LookingForGroupDetail";
    private const string Confirm = "SelectYesno";
    private const uint JoinButton = 109;
    private const int Yes = 0;
    private const int EquipSlots = 13;
    private const int WaistSlot = 5;
    private const int CountedSlots = 12;
    private static readonly TimeSpan Quiet = TimeSpan.FromMilliseconds(750);
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(5);
    private static readonly TimeSpan NoReply = TimeSpan.FromSeconds(2);

    private readonly Feed feed;
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
    private TaskCompletionSource<string?>? joining;
    private bool joinPressed;
    private DateTime joinBy;

    public PartyFinder(Feed feed)
    {
        this.feed = feed;
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

    public Task<string?> Open(OpenListing open) => Services.Framework.RunOnFrameworkThread(() => Show(open.Id));

    public Task<string?> Join(JoinListing join) => Services.Framework.RunOnFrameworkThread<Task<string?>>(() => StartJoin(join.Id)).Unwrap();

    public void Update(DateTime now)
    {
        StepJoin(now);
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
                    if (!answered && !retried && Blocked() == null && !WindowOpen() && Ask((byte)category))
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
        FinishJoin("not ready");
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
        if (wanted is < 1 or > Other)
            return "invalid";
        if (Blocked() is { } reason)
            return reason;
        if (WindowOpen())
            return "window open";
        if (!Ask(wanted))
            return "refused";
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

    private string? Show(ulong id)
    {
        if (Blocked() is { } reason)
            return reason;
        var agent = AgentLookingForGroup.Instance();
        return agent != null && agent->OpenListing(id) ? null : "refused";
    }

    private Task<string?> StartJoin(ulong id)
    {
        if (joining != null)
            return Task.FromResult<string?>("busy");
        if (Show(id) is { } reason)
            return Task.FromResult<string?>(reason);
        joining = new TaskCompletionSource<string?>();
        joinPressed = false;
        joinBy = DateTime.UtcNow + Timeout;
        return joining.Task;
    }

    private void StepJoin(DateTime now)
    {
        if (joining == null)
            return;
        if (now > joinBy)
        {
            FinishJoin("timeout");
            return;
        }
        if (!joinPressed)
        {
            var detail = Ready(Detail);
            var button = detail == null ? null : detail->GetComponentButtonById(JoinButton);
            if (button == null || !button->IsEnabled)
                return;
            detail->FireCallbackInt(Yes);
            joinPressed = true;
            return;
        }
        var confirm = Ready(Confirm);
        if (confirm != null)
        {
            confirm->FireCallbackInt(Yes);
            FinishJoin(null);
        }
    }

    private void FinishJoin(string? reason)
    {
        var task = joining;
        joining = null;
        task?.TrySetResult(reason);
    }

    private static AtkUnitBase* Ready(string name)
    {
        var addon = (AtkUnitBase*)Services.GameGui.GetAddonByName(name, 1).Address;
        return addon != null && addon->IsReady && addon->IsVisible ? addon : null;
    }

    private static int ItemLevel()
    {
        var container = InventoryManager.Instance()->GetInventoryContainer(InventoryType.EquippedItems);
        if (container == null)
            return 0;
        var items = Services.DataManager.GetExcelSheet<Sheets.Item>();
        var total = 0;
        for (var i = 0; i < EquipSlots; i++)
        {
            if (i == WaistSlot || items.GetRowOrDefault(container->GetInventorySlot(i)->GetBaseItemId()) is not { } item)
                continue;
            var level = (int)item.LevelItem.RowId;
            total += i == 0 && item.EquipSlotCategory.ValueNullable?.OffHand == -1 ? level * 2 : level;
        }
        return total / CountedSlots;
    }

    private static string? Blocked()
    {
        if (GameState.NotReady())
            return "not ready";
        if (Services.Condition[ConditionFlag.BoundByDuty] || Services.Condition[ConditionFlag.BoundByDuty56] || Services.Condition[ConditionFlag.BoundByDuty95])
            return "in duty";
        return null;
    }

    private void OnListing(IPartyFinderListing listing, IPartyFinderListingEventArgs args)
    {
        var item = Map(listing, ItemLevel());
        var hidden = !item.Own && (DutyLocked(listing) || IsBlocked(listing.Id));
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
        return agent != null && agent->CategoryTab is >= 1 and <= Other ? agent->CategoryTab : null;
    }

    private static Listing Map(IPartyFinderListing listing, int itemLevel)
    {
        var job = Services.PlayerState.ClassJob.RowId;
        var present = listing.RawJobsPresent.ToList();
        var jobs = Services.DataManager.GetExcelSheet<Sheets.ClassJob>();
        var slots = new List<Slot>();
        var open = new SortedSet<string>(StringComparer.Ordinal);
        var oneEach = listing[SearchAreaFlags.OnePlayerPerJob];
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
            foreach (var accepting in accepted)
            {
                if (accepting is { } row && !(oneEach && present.Contains((byte)row.RowId)))
                    open.Add(row.Abbreviation.ExtractText());
            }
            var roles = accepted.Select(j => Role(j?.Role ?? 0)).Distinct().ToList();
            slots.Add(new Slot("", roles.Count == 1 ? roles[0] : 0));
        }
        fits &= itemLevel >= listing.MinimumItemLevel;
        fits &= !oneEach || !present.Contains((byte)job);

        return new Listing(
            listing.Id.ToString(),
            DutyName(listing),
            listing.DutyType == DutyType.Normal ? DutyCodes.Of(listing.RawDuty) : "",
            CategoryOf(listing.Category),
            listing.Description.TextValue,
            listing.Name.TextValue,
            listing.HomeWorld.ValueNullable?.Name.ExtractText() ?? "",
            listing.SecondsRemaining,
            listing.MinimumItemLevel,
            slots,
            open.ToList(),
            fits,
            listing.ContentId == Services.PlayerState.ContentId,
            listing[SearchAreaFlags.Private],
            listing[ObjectiveFlags.Practice],
            listing[ObjectiveFlags.Loot],
            listing[ObjectiveFlags.DutyCompletion],
            listing[ConditionFlags.DutyComplete],
            listing.BeginnersWelcome);
    }

    internal static int Role(byte role) => role == 3 ? 2 : role;

    private static bool DutyLocked(IPartyFinderListing listing) =>
        listing.DutyType == DutyType.Normal
        && listing.Duty.ValueNullable is { ContentLinkType: 1 } duty
        && duty.Content.TryGetValue<Sheets.InstanceContent>(out var content)
        && !Services.UnlockState.IsInstanceContentUnlocked(content);

    private static int CategoryOf(DutyCategory category) => category == DutyCategory.None ? Other : BitOperations.Log2((uint)category);

    private static string DutyName(IPartyFinderListing listing) => DutyQueue.Capitalised(listing.DutyType switch
    {
        DutyType.Roulette => Services.DataManager.GetExcelSheet<Sheets.ContentRoulette>().GetRowOrDefault(listing.RawDuty)?.Name.ExtractText(),
        DutyType.Normal => listing.Duty.ValueNullable?.Name.ExtractText(),
        _ => null,
    }) ?? "";
}
