using Lumina.Excel.Sheets;

namespace PartyBell;

// The kind of group a listing recruits, matching the Normal, Alliance and Custom Match tabs of the recruitment window.
internal static class RecruitMode
{
    public const byte Normal = 0;
    public const byte Alliance = 1;
    public const byte CustomMatch = 2;
    public const int PartySize = 8;
    public const int TeamSize = 5;
    public const int Groups = 3;
    private const int LightParty = 4;
    private const uint CustomMatchMembers = 30;

    public static bool Fits(byte mode, int slots) => mode switch
    {
        Normal => slots is LightParty or PartySize,
        Alliance => slots == Groups * PartySize,
        CustomMatch => slots == 2 * TeamSize,
        _ => false,
    };

    public static bool IsCustomMatch(ContentFinderCondition duty) => duty.ContentMemberType.RowId == CustomMatchMembers;

    public static byte Of(ContentFinderCondition duty)
    {
        var members = duty.ContentMemberType.Value;
        return IsCustomMatch(duty) ? CustomMatch
            : members.PartyCount == Groups && members.MembersPerParty == PartySize ? Alliance
            : Normal;
    }
}
