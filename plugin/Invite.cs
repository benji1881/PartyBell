namespace PartyBell;

internal sealed record Invite(long Id, string Name, string World)
{
    public static Invite? Parse(byte[] json) => Request.Parse(json, "invite", root => new Invite(
        root.GetProperty("id").GetInt64(),
        root.GetProperty("name").GetString() ?? "",
        root.GetProperty("world").GetString() ?? ""));
}
