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

internal static class SecureChannel
{
    public const WebSocketCloseStatus NotPaired = (WebSocketCloseStatus)4001;
    private const int NonceSize = 16;
    private const int KeySize = 32;
    private const int MaxHelloSize = 1024;
    private static readonly byte[] ProofLabel = "partybell phone"u8.ToArray();
    private static readonly byte[] KeyLabel = "partybell key"u8.ToArray();

    public static async Task<byte[]?> Authenticate(WebSocket socket, IReadOnlyList<PairedPhone> phones, CancellationToken token)
    {
        var serverNonce = RandomNumberGenerator.GetBytes(NonceSize);
        var challenge = JsonSerializer.SerializeToUtf8Bytes(new { nonce = Convert.ToBase64String(serverNonce) });
        await socket.SendAsync(challenge, WebSocketMessageType.Text, true, token);

        var hello = await ReceiveHello(socket, token);
        var key = hello == null ? null : Verify(hello, serverNonce, phones);
        if (key == null)
            await socket.CloseOutputAsync(NotPaired, "not paired", token);
        return key;
    }

    private static byte[]? Verify(Hello hello, byte[] serverNonce, IReadOnlyList<PairedPhone> phones)
    {
        var phone = phones.FirstOrDefault(p => p.Id == hello.Id);
        if (phone == null || hello.Nonce.Length != NonceSize)
            return null;

        var secret = Base64Url.DecodeFromChars(phone.Secret);
        byte[] salt = [.. serverNonce, .. hello.Nonce];
        byte[] message = [.. ProofLabel, .. salt];
        var expected = HMACSHA256.HashData(secret, message);
        if (!CryptographicOperations.FixedTimeEquals(expected, hello.Proof))
            return null;

        return HKDF.DeriveKey(HashAlgorithmName.SHA256, secret, KeySize, salt, KeyLabel);
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
                root.GetProperty("id").GetString() ?? "",
                Convert.FromBase64String(root.GetProperty("nonce").GetString() ?? ""),
                Convert.FromBase64String(root.GetProperty("proof").GetString() ?? ""));
        }
        catch (Exception e) when (e is JsonException or FormatException or KeyNotFoundException or InvalidOperationException)
        {
            return null;
        }
    }

    private sealed record Hello(string Id, byte[] Nonce, byte[] Proof);
}
