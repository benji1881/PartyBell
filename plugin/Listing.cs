using System.Collections.Generic;

namespace PartyBell;

public sealed record Listing(
    string Id,
    string Duty,
    string Code,
    int Category,
    string Description,
    string Leader,
    string World,
    int Seconds,
    int Ilvl,
    IReadOnlyList<Slot> Slots,
    IReadOnlyList<string> Jobs,
    bool Fits,
    bool Own,
    bool Locked,
    bool Practice,
    bool Loot,
    bool Completion,
    bool Cleared,
    bool Beginners,
    bool Blocked);

public sealed record Slot(string Job, int Role);

public sealed record Job(string Name, int Role);
