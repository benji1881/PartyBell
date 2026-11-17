using System.Collections.Generic;

namespace PartyBell;

public sealed record Listing(
    string Id,
    string Duty,
    int Category,
    string Description,
    string Leader,
    string World,
    int Seconds,
    int Ilvl,
    IReadOnlyList<Slot> Slots,
    bool Fits,
    bool Own,
    bool Locked,
    bool Practice,
    bool Loot,
    bool Completion,
    bool Cleared,
    bool Beginners,
    bool CrossWorld);

public sealed record Slot(string Job, int Role);
