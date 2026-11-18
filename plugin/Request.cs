using System;
using System.Collections.Generic;
using System.Text.Json;

namespace PartyBell;

internal static class Request
{
    public static T? Parse<T>(byte[] json, string type, Func<JsonElement, T> read) where T : class
    {
        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            return root.GetProperty("type").GetString() == type ? read(root) : null;
        }
        catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException or FormatException or OverflowException)
        {
            return null;
        }
    }
}
