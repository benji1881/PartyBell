using System.Text.Json;

namespace PartyBell;

internal sealed record DutyAction(string Action)
{
    public const string Type = "duty";
    public const string Commence = "commence";
    public const string Withdraw = "withdraw";
    public const string Leave = "leave";

    public static DutyAction Read(JsonElement root) => new(root.GetProperty("action").GetString() ?? "");
}
