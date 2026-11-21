using System;
using System.Buffers.Text;
using System.Collections.Generic;
using System.Linq;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed record Session(PairedPhone Phone, byte[] SendKey, byte[] ReceiveKey, string Icons);

internal static class SecureChannel
{
    public const int Version = 7;
    public const WebSocketCloseStatus NotPaired = (WebSocketCloseStatus)4001;
    public const WebSocketCloseStatus UpdateNeeded = (WebSocketCloseStatus)4002;
    private const int NonceSize = 16;
    private const int KeySize = 32;
    private const int MaxHelloSize = 1024;
    private static readonly byte[] ProofLabel = "partybell phone"u8.ToArray();
    private static readonly byte[] ToPhoneLabel = "partybell to phone"u8.ToArray();
    private static readonly byte[] ToPcLabel = "partybell to pc"u8.ToArray();

    public static async Task<Session?> Authenticate(WebSocket socket, IReadOnlyList<PairedPhone> phones, CancellationToken token)
    {
        using var ephemeral = ECDiffieHellman.Create(ECCurve.NamedCurves.nistP256);
        var serverNonce = RandomNumberGenerator.GetBytes(NonceSize);
        var serverKey = ephemeral.ExportSubjectPublicKeyInfo();
        var challenge = JsonSerializer.SerializeToUtf8Bytes(new
        {
            v = Version,
            nonce = Convert.ToBase64String(serverNonce),
            ephemeral = Convert.ToBase64String(serverKey),
        });
        await socket.SendAsync(challenge, WebSocketMessageType.Text, true, token);

        var hello = await ReceiveHello(socket, token);
        if (hello != null && hello.Version != Version)
        {
            await socket.CloseOutputAsync(UpdateNeeded, $"v{Version}", token);
            return null;
        }

        var session = hello == null ? null : Verify(hello, serverNonce, ephemeral, serverKey, phones);
        if (session == null)
            await socket.CloseOutputAsync(NotPaired, "not paired", token);
        return session;
    }

    private static Session? Verify(Hello hello, byte[] serverNonce, ECDiffieHellman ephemeral, byte[] serverKey, IReadOnlyList<PairedPhone> phones)
    {
        var phone = phones.FirstOrDefault(p => p.Id == hello.Id);
        if (phone == null || hello.Nonce.Length != NonceSize)
            return null;

        var secret = Base64Url.DecodeFromChars(phone.Secret);
        byte[] salt = [.. serverNonce, .. hello.Nonce, .. serverKey, .. hello.Ephemeral];
        byte[] message = [.. ProofLabel, .. salt];
        var expected = HMACSHA256.HashData(secret, message);
        if (!CryptographicOperations.FixedTimeEquals(expected, hello.Proof))
            return null;

        var shared = Agree(ephemeral, hello.Ephemeral);
        if (shared == null)
            return null;

        byte[] keyMaterial = [.. secret, .. shared];
        return new Session(
            phone,
            HKDF.DeriveKey(HashAlgorithmName.SHA256, keyMaterial, KeySize, salt, ToPhoneLabel),
            HKDF.DeriveKey(HashAlgorithmName.SHA256, keyMaterial, KeySize, salt, ToPcLabel),
            hello.Icons);
    }

    private static byte[]? Agree(ECDiffieHellman ephemeral, byte[] phoneKey)
    {
        using var phone = ECDiffieHellman.Create();
        try
        {
            phone.ImportSubjectPublicKeyInfo(phoneKey, out _);
            return ephemeral.DeriveRawSecretAgreement(phone.PublicKey);
        }
        catch (Exception e) when (e is CryptographicException or ArgumentException)
        {
            return null;
        }
    }

    private static async Task<Hello?> ReceiveHello(WebSocket socket, CancellationToken token)
    {
        var buffer = new byte[MaxHelloSize];
        var result = await socket.ReceiveAsync(buffer.AsMemory(), token);
        if (result.MessageType != WebSocketMessageType.Text || !result.EndOfMessage)
            return null;

        try
        {
            using var json = JsonDocument.Parse(buffer.AsMemory(0, result.Count));
            var root = json.RootElement;
            return new Hello(
                root.TryGetProperty("v", out var version) ? version.GetInt32() : 1,
                root.GetProperty("id").GetString() ?? "",
                Convert.FromBase64String(root.GetProperty("nonce").GetString() ?? ""),
                root.TryGetProperty("ephemeral", out var ephemeral) ? Convert.FromBase64String(ephemeral.GetString() ?? "") : [],
                Convert.FromBase64String(root.GetProperty("proof").GetString() ?? ""),
                root.TryGetProperty("icons", out var icons) ? icons.GetString() ?? "" : "");
        }
        catch (Exception e) when (e is JsonException or FormatException or KeyNotFoundException or InvalidOperationException)
        {
            return null;
        }
    }

    private sealed record Hello(int Version, string Id, byte[] Nonce, byte[] Ephemeral, byte[] Proof, string Icons);
}
