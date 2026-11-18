using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using Lumina.Data.Files;
using Lumina.Excel.Sheets;

namespace PartyBell;

internal static class JobIcons
{
    private const uint IconBase = 62800;
    private const float GlyphBrightness = 130;
    private const float GlyphSoftness = 50;
    private const float RoleThreshold = 120;
    private const float RoleSoftness = 80;
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
            var mask = Mask(texture.ImageData);
            hash.AppendData(Encoding.UTF8.GetBytes(abbreviation));
            hash.AppendData(mask);
            icons[abbreviation] = Convert.ToBase64String(mask);
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
            var mask = RoleMask(texture.ImageData, plain.ImageData, roleSize);
            hash.AppendData(Encoding.UTF8.GetBytes(role));
            hash.AppendData(mask);
            roles[role] = Convert.ToBase64String(mask);
        }

        var version = Convert.ToHexStringLower(hash.GetHashAndReset(), 0, 8);
        return (version, Messages.Icons(version, size, icons, roleSize, roles));
    }

    private static TexFile? Texture(uint id) => Services.DataManager.GetFile<TexFile>($"ui/icon/{id / 1000 * 1000:D6}/{id:D6}_hr1.tex");

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

        return soft.Select((value, p) => (byte)(255 * (outside[p] ? value : 1))).ToArray();
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
