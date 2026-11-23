using System.Globalization;
using System.Text;
using Dalamud.Game.Text.SeStringHandling;
using Dalamud.Game.Text.SeStringHandling.Payloads;

namespace PartyBell;

internal sealed record ChatLink(string Text, string Listing, string Link)
{
    public static ChatLink Read(SeString message)
    {
        var text = new StringBuilder();
        var link = new StringBuilder();
        var listing = "";
        var inLink = false;
        foreach (var payload in message.Payloads)
        {
            if (payload is PartyFinderPayload finder)
            {
                listing = finder.ListingId.ToString(CultureInfo.InvariantCulture);
                inLink = true;
            }
            else if (inLink && RawPayload.LinkTerminator.Equals(payload))
                inLink = false;
            else if (payload is ITextProvider provider)
                (inLink ? link : text).Append(provider.Text);
        }
        return new ChatLink(text.ToString().Trim(), listing, link.ToString().Trim());
    }
}
