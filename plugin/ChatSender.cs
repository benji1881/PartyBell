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
    private readonly List<(string Text, TaskCompletionSource Done)> undelivered = [];
    private string replyTo = "";

    public void Received(string from) => replyTo = from;

    public async Task<string?> Send(Say say)
    {
        if (!config.AllowPhoneChat)
            return Reason.Disabled;

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
            return Reason.Invalid;
        if (text.StartsWith('/'))
            return Reason.Command;

        var line = prefix + text;
        if (Encoding.UTF8.GetByteCount(line) > MaxBytes)
            return Reason.TooLong;

        if (say.Channel != Tell)
            return await Services.Framework.RunOnFrameworkThread(() => Post(say.Channel, line));

        var delivery = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var reason = await Services.Framework.RunOnFrameworkThread(() =>
        {
            undelivered.Add((text, delivery));
            return Post(say.Channel, IsReply(say) ? ReplyPrefix + text : line);
        });
        if (reason == null && await Task.WhenAny(delivery.Task, Task.Delay(DeliveryTimeout)) == delivery.Task)
            return null;
        await Services.Framework.RunOnFrameworkThread(() => undelivered.RemoveAll(t => t.Done == delivery));
        return reason ?? Reason.NotDelivered;
    }

    public void Delivered(string text)
    {
        var index = undelivered.FindIndex(t => t.Text == text);
        if (index < 0)
            return;
        undelivered[index].Done.TrySetResult();
        undelivered.RemoveAt(index);
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
