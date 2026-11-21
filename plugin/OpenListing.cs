using System.Text.Json;

namespace PartyBell;

internal sealed record OpenListing(ulong Id)
{
    public const string Type = "open";

    public static OpenListing Read(JsonElement root) => new(ulong.Parse(root.GetProperty("id").GetString() ?? ""));
}
