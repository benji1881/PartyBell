using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record BisSlot(int Slot, string Name, int Level, uint Icon, bool Done);

internal sealed record BisItem(string Name, uint Icon);

internal sealed record BisMateria(string Name, uint Icon, string Stat, int Value, int Count);

internal sealed record BisStat(string Name, int Value);

internal sealed record BisView(string Name, IReadOnlyList<BisSlot> Slots, BisItem? Food, IReadOnlyList<BisMateria> Materia, double Gcd, IReadOnlyList<BisStat> Stats);

// Keeps a best in slot set per job and compares it with what is equipped.
internal sealed class BestInSlot(Configuration config)
{
    private const string WrongJob = "wrong job";
    private static readonly TimeSpan Retry = TimeSpan.FromMinutes(1);
    private static readonly int[] Rings = [11, 12];

    private readonly ConcurrentDictionary<string, GearSet> sets = new();
    private readonly ConcurrentDictionary<string, DateTime> loading = new();

    public async Task<string?> Link(string link)
    {
        var job = await Services.Framework.RunOnFrameworkThread(() => Services.PlayerState.IsLoaded ? Services.PlayerState.ClassJob.RowId : 0);
        if (job == 0)
            return Reason.NotReady;
        if (link.Trim().Length == 0)
        {
            await Save(job, null);
            return null;
        }
        if (GearSets.Key(link) is not { } id)
            return Reason.Invalid;
        var set = await Load(id);
        if (set == null)
            return Reason.NotFound;
        if (set.Job != job)
            return WrongJob;
        await Save(job, id);
        return null;
    }

    public string Message()
    {
        if (!Services.PlayerState.IsLoaded || !config.BestInSlot.TryGetValue(Services.PlayerState.ClassJob.RowId, out var id))
            return Messages.Bis(null, null);
        if (sets.TryGetValue(id, out var set))
            return Messages.Bis(GearSets.Link(id), View(set));
        if (DateTime.UtcNow >= loading.GetValueOrDefault(id))
            _ = Load(id);
        return Messages.Bis(GearSets.Link(id), null);
    }

    private async Task<GearSet?> Load(string id)
    {
        loading[id] = DateTime.UtcNow + Retry;
        try
        {
            if (await GearSets.Fetch(id) is { } set)
                return sets[id] = set;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or JsonException or KeyNotFoundException or InvalidOperationException or FormatException)
        {
            Services.Log.Warning(e, "Could not read the best in slot set.");
        }
        return null;
    }

    private Task Save(uint job, string? id) => Services.Framework.RunOnFrameworkThread(() =>
    {
        if (id == null)
            config.BestInSlot.Remove(job);
        else
            config.BestInSlot[job] = id;
        config.Save();
    });

    private static BisView View(GearSet set)
    {
        var items = Services.DataManager.GetExcelSheet<Sheets.Item>();
        var equipped = Gear.Ids();
        var slots = new List<BisSlot>();
        foreach (var (slot, id) in Placed(set.Slots, equipped).OrderBy(s => s.Key))
        {
            if (items.GetRowOrDefault(id) is { } item)
                slots.Add(new BisSlot(slot, item.Name.ExtractText(), (int)item.LevelItem.RowId, item.Icon, equipped.GetValueOrDefault(slot) == id));
        }
        var food = items.GetRowOrDefault(set.Food) is { } meal ? new BisItem(meal.Name.ExtractText(), meal.Icon) : null;
        return new BisView(set.Name, slots, food, Materia(set.Materia), CurrentStats.Gcd(), CurrentStats.Read());
    }

    private static Dictionary<int, uint> Placed(IReadOnlyDictionary<int, uint> slots, IReadOnlyDictionary<int, uint> equipped)
    {
        var placed = slots.Where(s => !Rings.Contains(s.Key)).ToDictionary();
        var rings = Rings.Where(slots.ContainsKey).Select(r => slots[r]).ToList();
        var open = new List<int>();
        foreach (var ring in Rings)
        {
            if (rings.Remove(equipped.GetValueOrDefault(ring)))
                placed[ring] = equipped[ring];
            else
                open.Add(ring);
        }
        foreach (var (ring, item) in open.Zip(rings))
            placed[ring] = item;
        return placed;
    }

    private static List<BisMateria> Materia(IReadOnlyDictionary<uint, int> totals)
    {
        var items = Services.DataManager.GetExcelSheet<Sheets.Item>();
        var list = new List<BisMateria>();
        foreach (var row in Services.DataManager.GetExcelSheet<Sheets.Materia>())
        {
            for (var grade = 0; grade < row.Item.Count && grade < row.Value.Count; grade++)
            {
                if (totals.TryGetValue(row.Item[grade].RowId, out var count) && items.GetRowOrDefault(row.Item[grade].RowId) is { } item)
                    list.Add(new BisMateria(item.Name.ExtractText(), item.Icon, ItemStats.Name(row.BaseParam.RowId), row.Value[grade], count));
            }
        }
        return list.OrderByDescending(m => m.Count).ToList();
    }
}
