namespace PartyBell;

internal sealed record JoinListing(ulong Id)
{
    public static JoinListing? Parse(byte[] json) => Request.Parse(json, "join", root => new JoinListing(ulong.Parse(root.GetProperty("id").GetString() ?? "")));
}
