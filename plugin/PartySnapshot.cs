using System.Collections.Generic;
using System.Linq;

namespace PartyBell;

public sealed record PartySnapshot(IReadOnlyList<Member> Members, int Capacity)
{
    public static readonly PartySnapshot Empty = new([], PartyReader.GroupSize);

    public bool IsFull => Members.Count >= Capacity;

    public bool SameAs(PartySnapshot other) => Capacity == other.Capacity && Members.SequenceEqual(other.Members);
}
