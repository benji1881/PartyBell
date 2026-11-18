namespace PartyBell;

internal sealed record OpenListing(ulong Id)
{
    public static OpenListing? Parse(byte[] json) => Request.Parse(json, "open", root => new OpenListing(ulong.Parse(root.GetProperty("id").GetString() ?? "")));
}
