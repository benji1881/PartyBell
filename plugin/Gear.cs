using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Game;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record GearPiece(int Slot, string Name, int Level, int Condition, int Spiritbond, uint Icon, string Category, IReadOnlyList<ItemStat> Stats, IReadOnlyList<ItemBonus> Bonuses);

internal static unsafe class Gear
{
    private const int EquipSlots = 13;
    private const int WaistSlot = 5;
    private const int CountedSlots = 12;
    private const int ConditionScale = 300;
    private const int SpiritbondScale = 100;

    public static int ItemLevel()
    {
        var total = 0;
        foreach (var (index, _, item) in Equipped())
        {
            var level = (int)item.LevelItem.RowId;
            total += index == 0 && item.EquipSlotCategory.ValueNullable?.OffHand == -1 ? level * 2 : level;
        }
        return total / CountedSlots;
    }

    public static Dictionary<int, uint> Ids() => Equipped().ToDictionary(e => e.Index, e => e.Item.RowId);

    public static List<GearPiece> Read()
    {
        var pieces = new List<GearPiece>();
        foreach (var (index, slot, item) in Equipped())
        {
            pieces.Add(new GearPiece(
                index,
                item.Name.ExtractText(),
                (int)item.LevelItem.RowId,
                slot.Condition / ConditionScale,
                slot.SpiritbondOrCollectability / SpiritbondScale,
                item.Icon,
                item.ItemUICategory.ValueNullable?.Name.ExtractText() ?? "",
                ItemStats.Header(item, slot),
                ItemStats.Bonuses(item, slot)));
        }
        return pieces;
    }

    private static List<(int Index, InventoryItem Slot, Sheets.Item Item)> Equipped()
    {
        var equipped = new List<(int, InventoryItem, Sheets.Item)>();
        var container = InventoryManager.Instance()->GetInventoryContainer(InventoryType.EquippedItems);
        if (container == null)
            return equipped;
        var items = Services.DataManager.GetExcelSheet<Sheets.Item>();
        for (var i = 0; i < EquipSlots; i++)
        {
            var slot = container->GetInventorySlot(i);
            if (i != WaistSlot && slot != null && slot->GetBaseItemId() is not 0 and var id && items.GetRowOrDefault(id) is { } item)
                equipped.Add((i, *slot, item));
        }
        return equipped;
    }
}
