using System.Text.Json;

namespace PartyBell;

internal sealed record Say(long Id, string Channel, string Text, string To)
{
    public const string Type = "say";

    public static Say Read(JsonElement root) => new(
        root.GetProperty("id").GetInt64(),
        root.GetProperty("channel").GetString() ?? "",
        root.GetProperty("text").GetString() ?? "",
        root.TryGetProperty("to", out var to) ? to.GetString() ?? "" : "");
}
