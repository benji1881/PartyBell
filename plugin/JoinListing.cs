using System.Text.Json;

namespace PartyBell;

internal sealed record JoinListing(ulong Id)
{
    public const string Type = "join";

    public static JoinListing Read(JsonElement root) => new(ulong.Parse(root.GetProperty("id").GetString() ?? ""));
}
