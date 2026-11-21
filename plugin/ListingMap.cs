using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using Dalamud.Game.Gui.PartyFinder.Types;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Turns a game Party Finder listing into what the phone shows.
internal static class ListingMap
{
    public const byte Other = 16;

    public static Listing From(IPartyFinderListing listing, int itemLevel)
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
                slots.Add(new Slot(member?.Abbreviation.ExtractText() ?? "", JobRole.Grouped(member?.Role ?? 0)));
                continue;
            }
            var accepted = slot.Accepting.Select(f => f.ClassJob(Services.DataManager)).ToList();
            fits |= accepted.Any(j => j?.RowId == job);
            foreach (var accepting in accepted)
            {
                if (accepting is { } row && !(oneEach && present.Contains((byte)row.RowId)))
                    open.Add(row.Abbreviation.ExtractText());
            }
            var roles = accepted.Select(j => JobRole.Grouped(j?.Role ?? 0)).Distinct().ToList();
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
            listing.BeginnersWelcome,
            false);
    }

    public static bool DutyLocked(IPartyFinderListing listing) =>
        listing.DutyType == DutyType.Normal
        && listing.Duty.ValueNullable is { ContentLinkType: 1 } duty
        && duty.Content.TryGetValue<Sheets.InstanceContent>(out var content)
        && !Services.UnlockState.IsInstanceContentUnlocked(content);

    private static int CategoryOf(DutyCategory category) => category == DutyCategory.None ? Other : BitOperations.Log2((uint)category);

    private static string DutyName(IPartyFinderListing listing) => GameText.Capitalised(listing.DutyType switch
    {
        DutyType.Roulette => Services.DataManager.GetExcelSheet<Sheets.ContentRoulette>().GetRowOrDefault(listing.RawDuty)?.Name.ExtractText(),
        DutyType.Normal => listing.Duty.ValueNullable?.Name.ExtractText(),
        _ => null,
    }) ?? "";
}
