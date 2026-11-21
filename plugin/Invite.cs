using System.Text.Json;

namespace PartyBell;

internal sealed record Invite(long Id, string Name, string World)
{
    public const string Type = "invite";

    public static Invite Read(JsonElement root) => new(
        root.GetProperty("id").GetInt64(),
        root.GetProperty("name").GetString() ?? "",
        root.GetProperty("world").GetString() ?? "");
}
