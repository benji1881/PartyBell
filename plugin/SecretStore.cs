using System;
using System.Security.Cryptography;
using System.Text;

namespace PartyBell;

// Encrypts phone secrets for this user account before they are saved, so a copied or shared config can't pose as a phone.
// On Linux and Mac the game runs in Wine, which provides the same Windows encryption.
internal static class SecretStore
{
    private const string Prefix = "dpapi:";
    private static readonly byte[] Entropy = "PartyBell phone secret"u8.ToArray();

    public static string Protect(string secret)
    {
        try
        {
            return Prefix + Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(secret), Entropy, DataProtectionScope.CurrentUser));
        }
        catch (Exception e) when (e is CryptographicException or PlatformNotSupportedException)
        {
            Services.Log.Warning(e, "Couldn't encrypt a phone secret, so it is saved unencrypted.");
            return secret;
        }
    }

    // Secrets saved before they were encrypted are read as they are and encrypted on the next save.
    public static string Unprotect(string saved)
    {
        if (!saved.StartsWith(Prefix, StringComparison.Ordinal))
            return saved;
        try
        {
            return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(saved[Prefix.Length..]), Entropy, DataProtectionScope.CurrentUser));
        }
        catch (Exception e) when (e is CryptographicException or FormatException or PlatformNotSupportedException)
        {
            Services.Log.Warning(e, "Couldn't read a phone secret, so that phone has to pair again.");
            return "";
        }
    }
}
