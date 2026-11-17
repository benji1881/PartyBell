using System;
using System.Buffers.Binary;
using System.Security.Cryptography;

namespace PartyBell;

internal sealed class Sealer(Session session) : IDisposable
{
    public const int TagSize = 16;
    private const int NonceSize = 12;

    private readonly AesGcm sendAes = new(session.SendKey, TagSize);
    private readonly AesGcm receiveAes = new(session.ReceiveKey, TagSize);
    private ulong sendCounter;
    private ulong receiveCounter;

    public byte[] Seal(ReadOnlySpan<byte> plain)
    {
        Span<byte> nonce = stackalloc byte[NonceSize];
        BinaryPrimitives.WriteUInt64BigEndian(nonce[4..], sendCounter++);
        var output = new byte[plain.Length + TagSize];
        sendAes.Encrypt(nonce, plain, output.AsSpan(0, plain.Length), output.AsSpan(plain.Length));
        return output;
    }

    public byte[]? Open(ReadOnlySpan<byte> sealedData)
    {
        if (sealedData.Length < TagSize)
            return null;

        Span<byte> nonce = stackalloc byte[NonceSize];
        BinaryPrimitives.WriteUInt64BigEndian(nonce[4..], receiveCounter);
        var plain = new byte[sealedData.Length - TagSize];
        try
        {
            receiveAes.Decrypt(nonce, sealedData[..plain.Length], sealedData[plain.Length..], plain);
        }
        catch (CryptographicException)
        {
            return null;
        }
        receiveCounter++;
        return plain;
    }

    public void Dispose()
    {
        sendAes.Dispose();
        receiveAes.Dispose();
    }
}
