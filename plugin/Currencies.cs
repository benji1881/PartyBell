using System;
using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Game;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record Currency(uint Id, string Name, long Count);

internal sealed record CurrencyItem(uint Id, string Name, uint Icon);

internal static unsafe class Currencies
{
    private const uint SortCategory = 3;

    private static readonly Lazy<List<CurrencyItem>> Items = new(() => Services.DataManager.GetExcelSheet<Sheets.Item>()
        .Where(item => item.ItemSortCategory.RowId == SortCategory && !item.Name.IsEmpty)
        .Select(item => new CurrencyItem(item.RowId, item.Name.ExtractText(), item.Icon))
        .ToList());

    public static IReadOnlyList<CurrencyItem> All => Items.Value;

    public static List<Currency> Owned()
    {
        var owned = new List<Currency>();
        var inventory = InventoryManager.Instance();
        var currency = CurrencyManager.Instance();
        if (inventory == null)
            return owned;
        foreach (var item in All)
        {
            long count = inventory->GetInventoryItemCount(item.Id);
            if (count == 0 && currency != null)
                count = currency->GetItemCount(item.Id);
            if (count > 0)
                owned.Add(new Currency(item.Id, item.Name, count));
        }
        return owned;
    }
}
