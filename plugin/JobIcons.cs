using System;
using System.Collections.Generic;
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

            var id = IconBase + job.RowId;
            var texture = Services.DataManager.GetFile<TexFile>($"ui/icon/{id / 1000 * 1000:D6}/{id:D6}_hr1.tex");
            if (texture == null || texture.Header.Width != texture.Header.Height || (size != 0 && texture.Header.Width != size))
                continue;

            size = texture.Header.Width;
            var mask = Mask(texture.ImageData);
            hash.AppendData(Encoding.UTF8.GetBytes(abbreviation));
            hash.AppendData(mask);
            icons[abbreviation] = Convert.ToBase64String(mask);
        }

        var version = Convert.ToHexStringLower(hash.GetHashAndReset(), 0, 8);
        return (version, Messages.Icons(version, size, icons));
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
