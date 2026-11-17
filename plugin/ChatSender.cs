using System.Linq;
using System.Text.RegularExpressions;
using System.Text;
using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.System.String;
using FFXIVClientStructs.FFXIV.Client.UI;

namespace PartyBell;

internal sealed partial class ChatSender(Configuration config, PartyTracker tracker)
{
    public const string Party = "party";
    public const string Alliance = "alliance";
    public const string FreeCompany = "fc";
    public const string Tell = "tell";
    private const int MaxBytes = 500;

    public async Task<string?> Send(Say say)
    {
        if (!config.AllowPhoneChat)
            return "disabled";

        var prefix = say.Channel switch
        {
            Party => "/p ",
            Alliance => "/a ",
            FreeCompany => "/fc ",
            Tell when Recipient().IsMatch(say.To) => $"/tell {say.To} ",
            _ => null,
        };
        var text = say.Text.Trim();
        if (prefix == null || text.Length == 0 || text.Any(char.IsControl))
            return "invalid";
        if (text.StartsWith('/'))
            return "command";

        var line = prefix + text;
        if (Encoding.UTF8.GetByteCount(line) > MaxBytes)
            return "too long";

        return await Services.Framework.RunOnFrameworkThread(() => Post(say.Channel, line));
    }

    private unsafe string? Post(string channel, string line)
    {
        if (!Services.PlayerState.IsLoaded)
            return "not ready";

        var party = tracker.Current;
        if (channel == Alliance && party is not { Capacity: > PartyReader.GroupSize })
            return "no alliance";
        if (channel == Party && party is not { Members.Count: > 1 })
            return "no party";

        var message = Utf8String.FromString(line);
        try
        {
            message->SanitizeString(
                AllowedEntities.UppercaseLetters | AllowedEntities.LowercaseLetters | AllowedEntities.Numbers |
                AllowedEntities.SpecialCharacters | AllowedEntities.CharacterList | AllowedEntities.OtherCharacters |
                AllowedEntities.Payloads | AllowedEntities.Unknown9);
            if (message->ToString() != line)
                return "invalid";
            UIModule.Instance()->ProcessChatBoxEntry(message);
            return null;
        }
        finally
        {
            message->Dtor(true);
        }
    }

    [GeneratedRegex(@"^[A-Za-z][A-Za-z'\-]* [A-Za-z][A-Za-z'\-]*@[A-Za-z]+$")]
    private static partial Regex Recipient();
}
