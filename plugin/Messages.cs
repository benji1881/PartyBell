using System;
using System.Collections.Generic;
using System.Text.Json;

namespace PartyBell;

internal static class Messages
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web);

    public static string Party(PartySnapshot party) =>
        Json(new { type = "party", members = party.Members, capacity = party.Capacity });

    public static string Event(PartyEvent e) =>
        Json(new { type = "event", kind = e.Kind, name = e.Member?.Name ?? "", job = e.Member?.Job ?? "", role = e.Member?.Role ?? 0, time = Now });

    public static string Chat(string name, Member? member, string text, string channel) =>
        Json(new { type = "chat", name, job = member?.Job ?? "", role = member?.Role ?? 0, text, channel, time = Now });

    public static string History(IEnumerable<string> items) =>
        $"{{\"type\":\"history\",\"items\":[{string.Join(',', items)}]}}";

    private static long Now => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private static string Json(object value) => JsonSerializer.Serialize(value, Options);
}
