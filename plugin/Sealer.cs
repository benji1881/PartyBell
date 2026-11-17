using System;
using System.Buffers.Binary;
using System.Security.Cryptography;

namespace PartyBell;

internal sealed class Sealer(byte[] key) : IDisposable
{
    public const int TagSize = 16;
    private const int NonceSize = 12;

    private readonly AesGcm aes = new(key, TagSize);
    private ulong counter;

    public byte[] Seal(ReadOnlySpan<byte> plain)
    {
        Span<byte> nonce = stackalloc byte[NonceSize];
        BinaryPrimitives.WriteUInt64BigEndian(nonce[4..], counter++);
        var output = new byte[plain.Length + TagSize];
        aes.Encrypt(nonce, plain, output.AsSpan(0, plain.Length), output.AsSpan(plain.Length));
        return output;
    }

    public void Dispose() => aes.Dispose();
}
