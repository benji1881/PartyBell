using System;
using System.Collections.Generic;
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
    private const string ReplyPrefix = "/r ";
    private static readonly TimeSpan DeliveryTimeout = TimeSpan.FromSeconds(8);
    private readonly List<(string Text, TaskCompletionSource<string> Done)> undelivered = [];
    private string replyTo = "";

    public void Received(string from) => replyTo = from;

    public async Task<(string? Reason, string Error)> Send(Say say)
    {
        var text = say.Text.Trim();
        var prefix = Prefix(say);
        if (Refusal(prefix, text) is { } refused)
            return (refused, "");

        var line = prefix + text;
        if (say.Channel != Tell)
            return (await Services.Framework.RunOnFrameworkThread(() => Post(say.Channel, line)), "");

        var delivery = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        var reason = await Services.Framework.RunOnFrameworkThread(() =>
        {
            undelivered.Add((text, delivery));
            return Post(say.Channel, IsReply(say) ? ReplyPrefix + text : line);
        });
        if (reason == null && await Task.WhenAny(delivery.Task, Task.Delay(DeliveryTimeout)) == delivery.Task)
            return delivery.Task.Result.Length == 0 ? (null, "") : (Reason.Game, delivery.Task.Result);
        await Services.Framework.RunOnFrameworkThread(() => undelivered.RemoveAll(t => t.Done == delivery));
        return (reason ?? Reason.NotDelivered, "");
    }

    public void Delivered(string text) => Finish(undelivered.FindIndex(t => t.Text == text), "");

    public void Failed(string error) => Finish(undelivered.Count > 0 && error.Length > 0 ? 0 : -1, error);

    private void Finish(int index, string error)
    {
        if (index < 0)
            return;
        undelivered[index].Done.TrySetResult(error);
        undelivered.RemoveAt(index);
    }

    private static string? Prefix(Say say) => say.Channel switch
    {
        Party => "/p ",
        Alliance => "/a ",
        FreeCompany => "/fc ",
        Tell when Recipient().IsMatch(say.To) => $"/tell {say.To} ",
        _ => null,
    };

    private string? Refusal(string? prefix, string text)
    {
        if (!config.AllowPhoneChat)
            return Reason.Disabled;
        if (prefix == null || text.Length == 0 || text.Any(char.IsControl))
            return Reason.Invalid;
        if (text.StartsWith('/'))
            return Reason.Command;
        return Encoding.UTF8.GetByteCount(prefix + text) > MaxBytes ? Reason.TooLong : null;
    }

    private unsafe string? Post(string channel, string line)
    {
        if (!Services.PlayerState.IsLoaded)
            return Reason.NotReady;

        var party = tracker.Current;
        if (channel == Alliance && party is not { Capacity: > PartyReader.GroupSize })
            return Reason.NoAlliance;
        if (channel == Party && party is not { Members.Count: > 1 })
            return Reason.NoParty;
        if (channel == FreeCompany && !Company.IsMember)
            return Reason.NoFreeCompany;

        var message = Utf8String.FromString(line);
        try
        {
            message->SanitizeString(
                AllowedEntities.UppercaseLetters | AllowedEntities.LowercaseLetters | AllowedEntities.Numbers |
                AllowedEntities.SpecialCharacters | AllowedEntities.CharacterList | AllowedEntities.OtherCharacters |
                AllowedEntities.Payloads | AllowedEntities.Unknown9);
            if (message->ToString() != line)
                return Reason.Unsupported;
            UIModule.Instance()->ProcessChatBoxEntry(message);
            return null;
        }
        finally
        {
            message->Dtor(true);
        }
    }

    private bool IsReply(Say say) => say.Channel == Tell && say.To.Equals(replyTo, StringComparison.OrdinalIgnoreCase);

    [GeneratedRegex(@"^[A-Za-z][A-Za-z'\-]* [A-Za-z][A-Za-z'\-]*@[A-Za-z]+$")]
    private static partial Regex Recipient();
}
