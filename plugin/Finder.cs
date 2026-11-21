using System.Text.Json;

namespace PartyBell;

internal sealed record Finder(byte Category)
{
    public const string Type = "finder";

    public static Finder Read(JsonElement root) => new(root.GetProperty("category").GetByte());
}
