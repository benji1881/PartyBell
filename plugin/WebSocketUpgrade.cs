using System;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace PartyBell;

internal static class WebSocketUpgrade
{
    private const string AcceptGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private const int MaxHeaderSize = 8192;

    public static async Task<bool> Accept(Stream stream, CancellationToken token)
    {
        var header = await ReadHeader(stream, token);
        var lines = header?.Split("\r\n") ?? [];
        var target = lines.FirstOrDefault()?.Split(' ') is ["GET", var path, ..] ? path : null;
        var key = lines.Skip(1)
            .Select(line => line.Split(':', 2))
            .Where(parts => parts.Length == 2 && parts[0].Trim().Equals("Sec-WebSocket-Key", StringComparison.OrdinalIgnoreCase))
            .Select(parts => parts[1].Trim())
            .FirstOrDefault();

        if (target == null || key == null)
            return await Reject(stream, "400 Bad Request", token);
        if (target != "/")
            return await Reject(stream, "404 Not Found", token);

        var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + AcceptGuid)));
        await Write(stream, $"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {accept}\r\n\r\n", token);
        return true;
    }

    private static async Task<string?> ReadHeader(Stream stream, CancellationToken token)
    {
        var buffer = new byte[MaxHeaderSize];
        for (var length = 0; length < buffer.Length;)
        {
            if (await stream.ReadAsync(buffer.AsMemory(length, 1), token) == 0)
                return null;
            length++;
            if (length >= 4 && buffer.AsSpan(length - 4, 4).SequenceEqual("\r\n\r\n"u8))
                return Encoding.ASCII.GetString(buffer, 0, length - 4);
        }
        return null;
    }

    private static async Task<bool> Reject(Stream stream, string status, CancellationToken token)
    {
        await Write(stream, $"HTTP/1.1 {status}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n", token);
        return false;
    }

    private static Task Write(Stream stream, string text, CancellationToken token) =>
        stream.WriteAsync(Encoding.ASCII.GetBytes(text), token).AsTask();
}
