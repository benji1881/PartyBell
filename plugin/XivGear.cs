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
    private const string Api = "https://api.xivgear.app/fulldata?url=";
    private const string Site = "https://";
    private const string Shortlink = "https://xivgear.app/?page=sl|";
    private const string HashPage = "/#/";
    private const string PagePrefix = "/?page=";
    private const string SetIndex = "onlySetIndex=";

    private static readonly Dictionary<uint, string> StatKeys = new()
    {
        [CurrentStats.Critical] = "crit", [CurrentStats.DirectHit] = "dhit", [CurrentStats.Determination] = "determination",
        [CurrentStats.SkillSpeed] = "skillspeed", [CurrentStats.SpellSpeed] = "spellspeed", [CurrentStats.Tenacity] = "tenacity", [CurrentStats.Piety] = "piety",
    };

    private static readonly (string Key, int Slot)[] SlotKeys =
    [
        ("Weapon", 0), ("OffHand", 1), ("Head", 2), ("Body", 3), ("Hand", 4), ("Legs", 6), ("Feet", 7),
        ("Ears", 8), ("Neck", 9), ("Wrist", 10), ("RingLeft", 11), ("RingRight", 12),
    ];

    public static string? Key(string link)
    {
        if (ShareLink().Match(link.Trim()) is { Success: true } share)
            return Shortlink + share.Groups[1].Value.ToLowerInvariant();
        if (SheetLink().Match(link.Trim()) is not { Success: true } match)
            return null;
        var parts = match.Groups[1].Value.TrimEnd('/').Split(HashPage, 2);
        return Site + (parts.Length == 2 ? parts[0] + PagePrefix + parts[1].Replace('/', '|') : parts[0]);
    }

    public static bool Owns(string key) => key.StartsWith(Site, StringComparison.Ordinal);

    public static async Task<GearSet?> Fetch(string key)
    {
        using var sheet = await GearSets.Get(Api + Uri.EscapeDataString(key));
        if (sheet == null)
            return null;
        var root = sheet.RootElement;
        var filled = root.GetProperty("sets").EnumerateArray()
            .Select((set, index) => (Set: set, Index: index))
            .Where(s => s.Set.TryGetProperty("items", out var items) && items.EnumerateObject().Any())
            .ToList();
        if (filled.Count == 0)
            return null;
        var job = Job(root.GetProperty("job").GetString());
        var level = root.TryGetProperty("level", out var lvl) && lvl.ValueKind == JsonValueKind.Number ? lvl.GetUInt32() : 0;
        var choices = filled.Count == 1 ? [] : filled.Select(s => (Choice(key, s.Set, s.Index, job, level), Read(s.Set, job.RowId, []))).ToList();
        return Read(filled[0].Set, job.RowId, choices);
    }

    private static GearSet Read(JsonElement set, uint job, IReadOnlyList<(BisChoice Choice, GearSet Set)> choices)
    {
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
        return new GearSet(job, Name(set), slots, food, materia, choices);
    }

    private static string Name(JsonElement set) => set.TryGetProperty("name", out var name) ? name.GetString() ?? "" : "";

    private static BisChoice Choice(string key, JsonElement set, int index, Sheets.ClassJob job, uint level)
    {
        var link = key + (key.Contains('?', StringComparison.Ordinal) ? "&" : "?") + SetIndex + index;
        if (job.RowId == 0 || !set.TryGetProperty("computedStats", out var computed))
            return new BisChoice(Name(set), link, 0, []);
        var stats = CurrentStats.Substats(job).Select(s => new BisStat(ItemStats.Name(s), Stat(computed, StatKeys[s]))).ToList();
        return new BisChoice(Name(set), link, CurrentStats.Gcd(Stat(computed, StatKeys[CurrentStats.Speed(job)]), level), stats);
    }

    private static int Stat(JsonElement computed, string name) =>
        computed.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number ? (int)value.GetDouble() : 0;

    private static Sheets.ClassJob Job(string? abbreviation) =>
        Services.DataManager.GetExcelSheet<Sheets.ClassJob>(ClientLanguage.English).FirstOrDefault(j => j.Abbreviation.ExtractText() == abbreviation);

    [GeneratedRegex(@"^(?:https?://)?((?:www\.|share\.)?xivgear\.app/\S+)$", RegexOptions.IgnoreCase)]
    private static partial Regex SheetLink();

    [GeneratedRegex(@"^(?:https?://)?share\.xivgear\.app/share/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/?$", RegexOptions.IgnoreCase)]
    private static partial Regex ShareLink();
}
