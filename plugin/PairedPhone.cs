using System;
using System.Buffers.Text;
using System.Security.Cryptography;

namespace PartyBell;

public sealed class PairedPhone
{
    private const int SecretSize = 32;

    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Secret { get; set; } = "";
    public DateTime PairedAt { get; set; }

    public static PairedPhone Create() => new()
    {
        Id = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(4)),
        Name = $"Phone added {DateTime.Now:yyyy-MM-dd HH:mm}",
        Secret = Base64Url.EncodeToString(RandomNumberGenerator.GetBytes(SecretSize)),
        PairedAt = DateTime.UtcNow,
    };
}
