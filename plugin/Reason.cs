namespace PartyBell;

// Why a phone request failed. The app turns each code into text, so keep these in step with Reasons.kt.
internal static class Reason
{
    public const string Disabled = "disabled";
    public const string NotReady = "not ready";
    public const string InDuty = "in duty";
    public const string WindowOpen = "window open";
    public const string Refused = "refused";
    public const string Invalid = "invalid";
    public const string Failed = "failed";
    public const string Busy = "busy";
    public const string Timeout = "timeout";
    public const string NoReadyCheck = "no ready check";
    public const string Answered = "answered";
    public const string NoPrompt = "no prompt";
    public const string NotQueued = "not queued";
    public const string NoWindow = "no window";
    public const string PartyFull = "party full";
    public const string NotLeader = "not leader";
    public const string NotFound = "not found";
    public const string Offline = "offline";
    public const string Command = "command";
    public const string TooLong = "too long";
    public const string NoAlliance = "no alliance";
    public const string NoParty = "no party";
    public const string NoFreeCompany = "no fc";
    public const string Unsupported = "unsupported";
    public const string NotDelivered = "not delivered";
    public const string Game = "game";
}
