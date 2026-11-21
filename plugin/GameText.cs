namespace PartyBell;

internal static class GameText
{
    // Game sheets keep some names in lower case, such as "the Praetorium".
    public static string? Capitalised(string? name) => string.IsNullOrWhiteSpace(name) ? null : char.ToUpperInvariant(name[0]) + name[1..];
}
