using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Game;
using Lumina.Excel;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record ItemStat(string Name, string Value);

internal sealed record ItemBonus(string Name, int Value);

internal static class ItemStats
{
    private const uint PhysicalDamage = 12;
    private const uint MagicDamage = 13;
    private const uint Delay = 14;
    private const uint BlockRate = 17;
    private const uint BlockStrength = 18;
    private const uint Defense = 21;
    private const uint MagicDefense = 24;
    private const string AutoAttack = "Auto-attack";
    private const double AttackDelay = 3000;
    private const double Millis = 1000;
    private const double PerMille = 1000;
    private const double Hundredths = 100;
    private static readonly HashSet<uint> HeaderParams = [PhysicalDamage, MagicDamage, Delay, BlockRate, BlockStrength, Defense, MagicDefense];

    public static List<ItemStat> Header(Sheets.Item item, InventoryItem slot)
    {
        var special = Special(item, slot);
        ItemStat Stat(uint param, int value) => new(Name(param), (value + special.GetValueOrDefault(param)).ToString(CultureInfo.InvariantCulture));
        if (item.DamagePhys > 0 || item.DamageMag > 0)
        {
            var magic = item.DamageMag > item.DamagePhys;
            var param = magic ? MagicDamage : PhysicalDamage;
            var damage = (magic ? item.DamageMag : item.DamagePhys) + special.GetValueOrDefault(param);
            var auto = Math.Floor(damage * item.Delayms / AttackDelay * Hundredths) / Hundredths;
            return [new(Name(param), damage.ToString(CultureInfo.InvariantCulture)), new(AutoAttack, auto.ToString("F2", CultureInfo.InvariantCulture)), new(Name(Delay), (item.Delayms / Millis).ToString("F2", CultureInfo.InvariantCulture))];
        }
        if (item.Block > 0 || item.BlockRate > 0)
            return [Stat(BlockStrength, item.Block), Stat(BlockRate, item.BlockRate)];
        if (item.DefensePhys > 0 || item.DefenseMag > 0)
            return [Stat(Defense, item.DefensePhys), Stat(MagicDefense, item.DefenseMag)];
        return [];
    }

    public static List<ItemBonus> Bonuses(Sheets.Item item, InventoryItem slot)
    {
        var totals = new Dictionary<uint, int>();
        for (var i = 0; i < item.BaseParam.Count; i++)
            Add(totals, item.BaseParam[i].RowId, item.BaseParamValue[i]);
        foreach (var (param, value) in Special(item, slot))
            Add(totals, param, value);

        var melded = new Dictionary<uint, int>();
        var materia = Services.DataManager.GetExcelSheet<Sheets.Materia>();
        for (var i = 0; i < slot.Materia.Length; i++)
        {
            if (slot.Materia[i] != 0 && materia.GetRowOrDefault(slot.Materia[i]) is { } row && slot.MateriaGrades[i] < row.Value.Count)
                Add(melded, row.BaseParam.RowId, row.Value[slot.MateriaGrades[i]]);
        }
        foreach (var (param, value) in melded)
        {
            var total = totals.GetValueOrDefault(param);
            totals[param] = Math.Max(total, Math.Min(total + value, Cap(item, param)));
        }
        return totals.Where(t => !HeaderParams.Contains(t.Key)).Select(t => new ItemBonus(Name(t.Key), t.Value)).ToList();
    }

    private static Dictionary<uint, int> Special(Sheets.Item item, InventoryItem slot)
    {
        var special = new Dictionary<uint, int>();
        if (!slot.Flags.HasFlag(InventoryItem.ItemFlags.HighQuality))
            return special;
        for (var i = 0; i < item.BaseParamSpecial.Count; i++)
            Add(special, item.BaseParamSpecial[i].RowId, item.BaseParamValueSpecial[i]);
        return special;
    }

    private static void Add(Dictionary<uint, int> totals, uint param, int value)
    {
        if (param != 0 && value != 0)
            totals[param] = totals.GetValueOrDefault(param) + value;
    }

    private static int Cap(Sheets.Item item, uint param)
    {
        var level = Services.DataManager.Excel.GetSheet<RawRow>(name: "ItemLevel").GetRowOrDefault(item.LevelItem.RowId);
        var row = Services.DataManager.GetExcelSheet<Sheets.BaseParam>().GetRowOrDefault(param);
        if (level is not { } values || row is not { } stat)
            return int.MaxValue;
        return (int)Math.Round(values.ReadUInt16Column((int)param - 1) * Percent(stat, item.EquipSlotCategory.RowId) / PerMille, MidpointRounding.AwayFromZero);
    }

    private static int Percent(Sheets.BaseParam stat, uint category) => category switch
    {
        1 or 14 => stat.OneHandWeaponPercent,
        2 => stat.OffHandPercent,
        3 => stat.HeadPercent,
        5 => stat.HandsPercent,
        7 => stat.LegsPercent,
        8 => stat.FeetPercent,
        9 => stat.EarringPercent,
        10 => stat.NecklacePercent,
        11 => stat.BraceletPercent,
        12 => stat.RingPercent,
        13 => stat.TwoHandWeaponPercent,
        _ => stat.ChestPercent,
    };

    public static string Name(uint param) =>
        Services.DataManager.GetExcelSheet<Sheets.BaseParam>().GetRowOrDefault(param)?.Name.ExtractText() ?? "";
}
