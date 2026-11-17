namespace PartyBell;

internal sealed record Say(long Id, string Channel, string Text)
{
    public static Say? Parse(byte[] json) => Request.Parse(json, "say", root => new Say(
        root.GetProperty("id").GetInt64(),
        root.GetProperty("channel").GetString() ?? "",
        root.GetProperty("text").GetString() ?? ""));
}
