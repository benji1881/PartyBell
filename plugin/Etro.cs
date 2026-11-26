using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace PartyBell;

// Reads a gear set from etro.gg.
internal static partial class Etro
{
    private const string Api = "https://etro.gg/api/";
    private const string Site = "https://etro.gg/gearset/";

    private static readonly (string Key, int Slot)[] SlotKeys =
    [
        ("weapon", 0), ("offHand", 1), ("head", 2), ("body", 3), ("hands", 4), ("legs", 6), ("feet", 7),
        ("ears", 8), ("neck", 9), ("wrists", 10), ("fingerL", 11), ("fingerR", 12),
    ];

    public static string? Id(string link) => GearsetLink().Match(link.Trim()) is { Success: true } match ? match.Groups[1].Value.ToLowerInvariant() : null;

    public static string Link(string id) => Site + id;

    public static async Task<GearSet?> Fetch(string id)
    {
        using var set = await Get($"gearsets/{id}/");
        if (set == null)
            return null;
        var root = set.RootElement;
        var slots = new Dictionary<int, uint>();
        foreach (var (key, slot) in SlotKeys)
        {
            if (root.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.Number)
                slots[slot] = item.GetUInt32();
        }
        var food = root.TryGetProperty("food", out var foodId) && foodId.ValueKind == JsonValueKind.Number ? await FoodItem(foodId.GetUInt32()) : 0;
        var totals = root.GetProperty("totalParams").EnumerateArray().ToList();
        return new GearSet(root.GetProperty("job").GetUInt32(), root.GetProperty("name").GetString() ?? "", slots, food, Counts(Total(totals, "Materia Totals")));
    }

    private static async Task<uint> FoodItem(uint id)
    {
        using var food = await Get($"food/{id}/");
        return food?.RootElement.GetProperty("item").GetUInt32() ?? 0;
    }

    private static JsonElement? Total(List<JsonElement> totals, string name) =>
        totals.FirstOrDefault(t => t.GetProperty("name").GetString() == name) is { ValueKind: JsonValueKind.Object } total ? total.GetProperty("value") : null;

    private static Dictionary<uint, int> Counts(JsonElement? values) =>
        values is { ValueKind: JsonValueKind.Object } map
            ? map.EnumerateObject().Where(p => p.Value.ValueKind == JsonValueKind.Number).ToDictionary(p => uint.Parse(p.Name, CultureInfo.InvariantCulture), p => p.Value.GetInt32())
            : [];

    private static Task<JsonDocument?> Get(string path) => GearSets.Get(Api + path);

    [GeneratedRegex(@"^(?:https?://)?(?:www\.)?etro\.gg/gearset/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/?(?:[?#].*)?$", RegexOptions.IgnoreCase)]
    private static partial Regex GearsetLink();
}
