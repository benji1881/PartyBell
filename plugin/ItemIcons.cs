using System.Collections.Generic;
using System.Linq;

namespace PartyBell;

internal sealed class ItemIcons
{
    private string key = "";
    private string message = Messages.ItemIcons(IconSet.None);

    public string Message(IReadOnlyList<GearPiece>? gear)
    {
        var ids = gear?.Select(piece => piece.Icon).Distinct().Order().ToList() ?? [];
        var next = string.Join(',', ids);
        if (next != key)
        {
            key = next;
            message = Messages.ItemIcons(ids.Count == 0 ? IconSet.None : JobIcons.Items(ids));
        }
        return message;
    }
}
