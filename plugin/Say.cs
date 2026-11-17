using System;
using System.Collections.Generic;
using System.Text.Json;

namespace PartyBell;

internal sealed record Say(long Id, string Channel, string Text)
{
    public static Say? Parse(byte[] json)
    {
        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            if (root.GetProperty("type").GetString() != "say")
                return null;
            return new Say(
                root.GetProperty("id").GetInt64(),
                root.GetProperty("channel").GetString() ?? "",
                root.GetProperty("text").GetString() ?? "");
        }
        catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException or FormatException)
        {
            return null;
        }
    }
}
