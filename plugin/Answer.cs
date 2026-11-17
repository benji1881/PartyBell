namespace PartyBell;

internal sealed record Answer(bool Ready)
{
    public static Answer? Parse(byte[] json) => Request.Parse(json, "answer", root => new Answer(root.GetProperty("ready").GetBoolean()));
}
