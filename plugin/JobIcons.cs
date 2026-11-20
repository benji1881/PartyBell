using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using Lumina.Data.Files;
using Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record IconSet(int Size, IReadOnlyDictionary<string, string> Icons);

internal static class JobIcons
{
    private const uint IconBase = 62800;
    private const uint NewAdventurer = 32;
    private const float GlyphBrightness = 130;
    private const float GlyphSoftness = 50;
    private const float RoleThreshold = 120;
    private const float RoleSoftness = 80;
    private const byte RoleInside = 230;
    private const int ShadeLowPercent = 10;
    private const int ShadeHighPercent = 90;
    private const float ShadeFloor = 0.35f;
    private const string ArmouryBoard = "ui/uld/ArmouryBoard_hr1.tex";
    private const int SymbolSize = 80;
    private const int BoardColumns = 4;
    private const uint Aetheryte = 60453;
    private const string AetheryteKey = "aetheryte";
    private const int CurrencySize = 40;
    private static readonly string[] BoardSlots = ["main", "head", "body", "hands", "", "legs", "feet", "off", "ears", "neck", "wrists", "ring"];
    private static readonly (string Role, uint Icon, uint Background)[] RoleIcons = [("tank", 62581, 62571), ("healer", 62582, 62572), ("dps", 62583, 62573)];

    private static readonly Lazy<(string Version, string Message)> Data = new(Build);

    public static string Version => Data.Value.Version;

    public static string Message => Data.Value.Message;

    private static (string, string) Build()
    {
        var icons = new SortedDictionary<string, string>(StringComparer.Ordinal);
        var size = 0;
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        foreach (var job in Services.DataManager.GetExcelSheet<ClassJob>())
        {
            var abbreviation = job.Abbreviation.ExtractText();
            if (job.RowId == 0 || abbreviation.Length == 0 || icons.ContainsKey(abbreviation))
                continue;

            var texture = Texture(IconBase + job.RowId);
            if (texture == null || texture.Header.Width != texture.Header.Height || (size != 0 && texture.Header.Width != size))
                continue;

            size = texture.Header.Width;
            Add(icons, hash, abbreviation, Mask(texture.ImageData));
        }

        var roles = new SortedDictionary<string, string>(StringComparer.Ordinal);
        var roleSize = 0;
        foreach (var (role, id, background) in RoleIcons)
        {
            var texture = Texture(id);
            var plain = Texture(background);
            if (texture == null || plain == null || texture.Header.Width != texture.Header.Height || plain.ImageData.Length != texture.ImageData.Length
                || (roleSize != 0 && texture.Header.Width != roleSize))
                continue;

            roleSize = texture.Header.Width;
            Add(roles, hash, role, RoleMask(texture.ImageData, plain.ImageData, roleSize));
        }

        var jobs = Services.DataManager.GetExcelSheet<ClassJob>()
            .Where(job => job.JobIndex > 0 && job.Role != 0)
            .OrderBy(job => job.UIPriority)
            .Select(job => new Job(job.Abbreviation.ExtractText(), PartyFinder.Role(job.Role)))
            .ToList();
        foreach (var job in jobs)
            hash.AppendData(Encoding.UTF8.GetBytes($"{job.Name}:{job.Role}"));

        var sprout = "";
        var sproutSize = 0;
        var status = Services.DataManager.GetExcelSheet<OnlineStatus>().GetRowOrDefault(NewAdventurer);
        var icon = status is { } row ? Texture(row.Icon) : null;
        if (icon != null && icon.Header.Width == icon.Header.Height)
        {
            sproutSize = icon.Header.Width;
            var rgba = Rgba(icon.ImageData);
            hash.AppendData(rgba);
            sprout = Convert.ToBase64String(rgba);
        }

        var symbols = Symbols(hash);
        var currencies = CurrencyIcons(hash);
        var version = Convert.ToHexStringLower(hash.GetHashAndReset(), 0, 8);
        return (version, Messages.Icons(version, size, icons, roleSize, roles, jobs, sproutSize, sprout, symbols, currencies));
    }

    private static IconSet Symbols(IncrementalHash hash)
    {
        var symbols = new SortedDictionary<string, string>(StringComparer.Ordinal);
        var board = Services.DataManager.GetFile<TexFile>(ArmouryBoard);
        for (var i = 0; board != null && i < BoardSlots.Length; i++)
        {
            if (BoardSlots[i].Length > 0)
                Add(symbols, hash, BoardSlots[i], Glyph(Tile(board, i % BoardColumns * SymbolSize, i / BoardColumns * SymbolSize)));
        }
        if (Texture(Aetheryte) is { } aetheryte)
            Add(symbols, hash, AetheryteKey, Glyph(Tile(aetheryte, (aetheryte.Header.Width - SymbolSize) / 2, (aetheryte.Header.Height - SymbolSize) / 2)));
        return new IconSet(SymbolSize, symbols);
    }

    private static IconSet CurrencyIcons(IncrementalHash hash)
    {
        var icons = new SortedDictionary<string, string>(StringComparer.Ordinal);
        foreach (var item in Currencies.All)
        {
            if (Texture(item.Icon, false) is { Header.Width: CurrencySize, Header.Height: CurrencySize } texture)
                Add(icons, hash, item.Id.ToString(CultureInfo.InvariantCulture), Rgba(texture.ImageData));
        }
        return new IconSet(CurrencySize, icons);
    }

    private static void Add(IDictionary<string, string> set, IncrementalHash hash, string key, byte[] data)
    {
        hash.AppendData(Encoding.UTF8.GetBytes(key));
        hash.AppendData(data);
        set[key] = Convert.ToBase64String(data);
    }

    private static TexFile? Texture(uint id, bool high = true) =>
        Services.DataManager.GetFile<TexFile>($"ui/icon/{id / 1000 * 1000:D6}/{id:D6}{(high ? "_hr1" : "")}.tex");

    private static byte[] Tile(TexFile texture, int left, int top)
    {
        var width = texture.Header.Width;
        var height = texture.Header.Height;
        var tile = new byte[SymbolSize * SymbolSize * 4];
        for (var y = 0; y < SymbolSize; y++)
        {
            for (var x = 0; x < SymbolSize; x++)
            {
                var (sx, sy) = (left + x, top + y);
                if (sx >= 0 && sy >= 0 && sx < width && sy < height)
                    Array.Copy(texture.ImageData, (sy * width + sx) * 4, tile, (y * SymbolSize + x) * 4, 4);
            }
        }
        return tile;
    }

    private static byte[] Glyph(byte[] bgra) => Shade(bgra, bgra.Where((_, i) => i % 4 == 3).ToArray());

    private static byte[] RoleMask(byte[] icon, byte[] background, int size)
    {
        var soft = new float[size * size];
        for (var p = 0; p < soft.Length; p++)
        {
            var i = p * 4;
            var difference = Math.Abs(icon[i] - background[i]) + Math.Abs(icon[i + 1] - background[i + 1]) + Math.Abs(icon[i + 2] - background[i + 2]);
            soft[p] = Math.Clamp((difference - RoleThreshold) / RoleSoftness, 0, 1);
        }

        var outside = new bool[soft.Length];
        var queue = new Queue<int>();
        void Visit(int p)
        {
            if (outside[p] || soft[p] >= 0.5f)
                return;
            outside[p] = true;
            queue.Enqueue(p);
        }
        for (var k = 0; k < size; k++)
        {
            Visit(k);
            Visit((size - 1) * size + k);
            Visit(k * size);
            Visit(k * size + size - 1);
        }
        while (queue.TryDequeue(out var p))
        {
            var x = p % size;
            if (x > 0) Visit(p - 1);
            if (x < size - 1) Visit(p + 1);
            if (p >= size) Visit(p - size);
            if (p < soft.Length - size) Visit(p + size);
        }

        var filled = soft.Select((value, p) => (byte)(255 * (outside[p] ? value : 1))).ToArray();
        return Shade(icon, filled);
    }

    private static byte[] Shade(byte[] icon, byte[] filled)
    {
        var brightness = filled.Select((_, p) => (icon[p * 4] + icon[p * 4 + 1] + icon[p * 4 + 2]) / 3f).ToArray();
        var inside = brightness.Where((_, p) => filled[p] > RoleInside).Order().ToArray();
        if (inside.Length == 0)
            return filled;
        var low = inside[inside.Length * ShadeLowPercent / 100];
        var range = Math.Max(inside[inside.Length * ShadeHighPercent / 100] - low, 1);
        return filled.Select((value, p) => (byte)(value * (ShadeFloor + (1 - ShadeFloor) * Math.Clamp((brightness[p] - low) / range, 0, 1)))).ToArray();
    }

    private static byte[] Rgba(byte[] bgra)
    {
        var rgba = new byte[bgra.Length];
        for (var i = 0; i < bgra.Length; i += 4)
        {
            rgba[i] = bgra[i + 2];
            rgba[i + 1] = bgra[i + 1];
            rgba[i + 2] = bgra[i];
            rgba[i + 3] = bgra[i + 3];
        }
        return rgba;
    }

    private static byte[] Mask(byte[] bgra)
    {
        var mask = new byte[bgra.Length / 4];
        for (var i = 0; i < mask.Length; i++)
        {
            var pixel = bgra.AsSpan(i * 4, 4);
            var brightness = (pixel[0] + pixel[1] + pixel[2]) / 3f;
            var glyph = Math.Clamp((GlyphBrightness - brightness) / GlyphSoftness, 0, 1);
            mask[i] = (byte)(pixel[3] * glyph);
        }
        return mask;
    }
}
