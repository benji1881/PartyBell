using System.Text.Json;

namespace PartyBell;

internal sealed record Answer(bool Ready)
{
    public const string Type = "answer";

    public static Answer Read(JsonElement root) => new(root.GetProperty("ready").GetBoolean());
}
