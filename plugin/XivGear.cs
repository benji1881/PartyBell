using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Dalamud.Game;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Reads a gear set from xivgear.app.
internal static partial class XivGear
{
    private const string Api = "https://api.xivgear.app/basedata?url=";
    private const string Site = "https://";
    private const string Shortlink = "https://xivgear.app/?page=sl|";

    private static readonly (string Key, int Slot)[] SlotKeys =
    [
        ("Weapon", 0), ("OffHand", 1), ("Head", 2), ("Body", 3), ("Hand", 4), ("Legs", 6), ("Feet", 7),
        ("Ears", 8), ("Neck", 9), ("Wrist", 10), ("RingLeft", 11), ("RingRight", 12),
    ];

    public static string? Key(string link)
    {
        if (ShareLink().Match(link.Trim()) is { Success: true } share)
            return Shortlink + share.Groups[1].Value.ToLowerInvariant();
        return SheetLink().Match(link.Trim()) is { Success: true } match ? Site + match.Groups[1].Value : null;
    }

    public static bool Owns(string key) => key.StartsWith(Site, StringComparison.Ordinal);

    public static async Task<GearSet?> Fetch(string key)
    {
        using var sheet = await GearSets.Get(Api + Uri.EscapeDataString(key));
        if (sheet == null)
            return null;
        var root = sheet.RootElement;
        var set = root.GetProperty("sets").EnumerateArray().FirstOrDefault(s => s.TryGetProperty("items", out var items) && items.EnumerateObject().Any());
        if (set.ValueKind != JsonValueKind.Object)
            return null;
        var slots = new Dictionary<int, uint>();
        var materia = new Dictionary<uint, int>();
        var items = set.GetProperty("items");
        foreach (var (name, slot) in SlotKeys)
        {
            if (!items.TryGetProperty(name, out var item) || !item.TryGetProperty("id", out var id) || id.GetInt64() <= 0)
                continue;
            slots[slot] = id.GetUInt32();
            if (!item.TryGetProperty("materia", out var melds))
                continue;
            foreach (var meld in melds.EnumerateArray())
            {
                if (meld.TryGetProperty("id", out var meldId) && meldId.GetInt64() > 0)
                    materia[meldId.GetUInt32()] = materia.GetValueOrDefault(meldId.GetUInt32()) + 1;
            }
        }
        var food = set.TryGetProperty("food", out var foodId) && foodId.ValueKind == JsonValueKind.Number && foodId.GetInt64() > 0 ? foodId.GetUInt32() : 0;
        return new GearSet(Job(root.GetProperty("job").GetString()), set.GetProperty("name").GetString() ?? "", slots, food, materia);
    }

    private static uint Job(string? abbreviation) =>
        Services.DataManager.GetExcelSheet<Sheets.ClassJob>(ClientLanguage.English).FirstOrDefault(j => j.Abbreviation.ExtractText() == abbreviation).RowId;

    [GeneratedRegex(@"^(?:https?://)?((?:www\.|share\.)?xivgear\.app/\S+)$", RegexOptions.IgnoreCase)]
    private static partial Regex SheetLink();

    [GeneratedRegex(@"^(?:https?://)?share\.xivgear\.app/share/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/?$", RegexOptions.IgnoreCase)]
    private static partial Regex ShareLink();
}
