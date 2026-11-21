using System;
using System.Buffers.Text;
using System.Security.Cryptography;
using Newtonsoft.Json;

namespace PartyBell;

public sealed class PairedPhone
{
    private const int SecretSize = 32;

    public string Id { get; set; } = "";
    public string Name { get; set; } = "";

    [JsonIgnore]
    public string Secret { get; set; } = "";

    public DateTime PairedAt { get; set; }

    // The config keeps the old name, so phones paired before encryption still load.
    [JsonProperty(nameof(Secret))]
    private string SavedSecret
    {
        get => SecretStore.Protect(Secret);
        set => Secret = SecretStore.Unprotect(value);
    }

    public static PairedPhone Create() => new()
    {
        Id = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(4)),
        Name = $"Phone added {DateTime.Now:yyyy-MM-dd HH:mm}",
        Secret = Base64Url.EncodeToString(RandomNumberGenerator.GetBytes(SecretSize)),
        PairedAt = DateTime.UtcNow,
    };
}
