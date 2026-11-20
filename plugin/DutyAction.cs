namespace PartyBell;

internal sealed record DutyAction(string Action)
{
    public const string Commence = "commence";
    public const string Withdraw = "withdraw";
    public const string Leave = "leave";

    public static DutyAction? Parse(byte[] json) => Request.Parse(json, "duty", root => new DutyAction(root.GetProperty("action").GetString() ?? ""));
}
