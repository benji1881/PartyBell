using System.Linq;
using System.Text.Json;

namespace PartyBell;

// The phone keeps gear icons it has seen, so it only asks for the ones it is missing.
internal sealed record ItemIconRequest(uint[] Ids)
{
    public const string Type = "itemicons";

    // A full set of gear is 13 pieces, so anything above a few sets is not a real request.
    private const int MaxIds = 40;

    public static ItemIconRequest Read(JsonElement root) =>
        new(root.GetProperty("ids").EnumerateArray().Take(MaxIds).Select(id => id.GetUInt32()).Distinct().ToArray());
}
