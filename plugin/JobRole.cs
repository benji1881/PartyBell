namespace PartyBell;

internal static class JobRole
{
    private const byte Melee = 2;
    private const byte Ranged = 3;

    // Party Finder slots and the job list only tell tanks, healers and DPS apart, so ranged counts as melee.
    public static int Grouped(byte role) => role == Ranged ? Melee : role;
}
