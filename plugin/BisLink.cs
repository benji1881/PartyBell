using System.Text.Json;

namespace PartyBell;

// An Etro link the phone sets as the current job's best in slot, or an empty one to clear it.
internal sealed record BisLink(string Link)
{
    public const string Type = "bislink";
    private const int MaxLength = 200;

    public static BisLink Read(JsonElement root) => new(root.GetProperty("link").GetString() is { Length: <= MaxLength } link ? link : "");
}
