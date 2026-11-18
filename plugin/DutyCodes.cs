using System;
using System.Collections.Generic;
using System.Linq;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal static class DutyCodes
{
    private const uint Raids = 5;
    private const string Savage = "_2";
    private static readonly string[] Letters = ["", "a", "o", "e", "p", "m"];
    private static readonly Lazy<Dictionary<uint, string>> Codes = new(Build);

    public static string Of(uint duty) => Codes.Value.GetValueOrDefault(duty, "");

    private static Dictionary<uint, string> Build()
    {
        var raids = Services.DataManager.GetExcelSheet<Sheets.ContentFinderCondition>()
            .Where(r => r.ContentType.RowId == Raids && r.RequiredExVersion.RowId < Letters.Length && Letters[r.RequiredExVersion.RowId] != "")
            .Select(r => (r.RowId, Ex: r.RequiredExVersion.RowId, Code: r.ShortCode.ExtractText()))
            .ToList();
        var codes = new Dictionary<uint, string>();
        foreach (var tier in raids.GroupBy(r => r.Ex))
        {
            var order = tier
                .Where(r => r.Code.EndsWith(Savage, StringComparison.Ordinal))
                .Select(r => r.Code[..^Savage.Length])
                .Distinct()
                .Order(StringComparer.Ordinal)
                .Select((code, index) => (code, index))
                .ToDictionary(p => p.code, p => p.index + 1);
            foreach (var raid in tier)
            {
                var savage = raid.Code.EndsWith(Savage, StringComparison.Ordinal);
                if (order.TryGetValue(savage ? raid.Code[..^Savage.Length] : raid.Code, out var number))
                    codes[raid.RowId] = $"{Letters[tier.Key]}{number}{(savage ? "s" : "n")}";
            }
        }
        return codes;
    }
}
