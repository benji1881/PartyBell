namespace PartyBell;

internal sealed record Finder(byte Category)
{
    public static Finder? Parse(byte[] json) => Request.Parse(json, "finder", root => new Finder(root.GetProperty("category").GetByte()));
}
