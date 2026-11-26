using System.Collections.Generic;
using System.Linq;
using Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record RecruitDuty(int Category, uint Id, string Name);

// The duties a listing can be posted for, newest first, leaving out ones the character hasn't unlocked.
internal static class RecruitDuties
{
    public const byte Dungeons = 2;
    public const byte Guildhests = 3;
    public const byte Trials = 4;
    public const byte Raids = 5;
    public const byte HighEnd = 6;
    public const byte Fates = 9;
    public const byte Hunt = 11;
    public const byte DeepDungeons = 13;
    public const byte VariantCriterion = 15;
    public static readonly byte[] Categories = [Dungeons, Guildhests, Trials, Raids, HighEnd, Fates, Hunt, DeepDungeons, VariantCriterion, ListingMap.Other];

    private const uint DungeonType = 2;
    private const uint GuildhestType = 3;
    private const uint TrialType = 4;
    private const uint RaidType = 5;
    private const uint DeepDungeonType = 21;
    private const uint UltimateType = 28;
    private const uint VariantType = 30;
    private const byte InstanceLink = 1;

    public static bool NeedsDuty(byte category) => category is not (Fates or Hunt or ListingMap.Other);

    public static IReadOnlyList<RecruitDuty> Read() =>
        Services.DataManager.GetExcelSheet<ContentFinderCondition>()
            .Where(Unlocked)
            .Select(d => (Duty: d, Category: CategoryOf(d)))
            .Where(d => d.Category != 0)
            .OrderByDescending(d => d.Duty.ClassJobLevelRequired)
            .ThenByDescending(d => d.Duty.ItemLevelRequired)
            .Select(d => new RecruitDuty(d.Category, d.Duty.RowId, d.Duty.Name.ExtractText()))
            .ToList();

    private static bool Unlocked(ContentFinderCondition duty) =>
        !duty.Name.IsEmpty
        && (duty.ContentLinkType != InstanceLink
            || (duty.Content.TryGetValue<InstanceContent>(out var content) && Services.UnlockState.IsInstanceContentUnlocked(content)));

    private static byte CategoryOf(ContentFinderCondition duty) =>
        duty.HighEndDuty || duty.ContentType.RowId == UltimateType
            ? HighEnd
            : duty.ContentType.RowId switch
            {
                DungeonType => Dungeons,
                GuildhestType => Guildhests,
                TrialType => Trials,
                RaidType => Raids,
                DeepDungeonType => DeepDungeons,
                VariantType => VariantCriterion,
                _ => (byte)0,
            };
}
