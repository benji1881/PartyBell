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

    public static string Chat(string name, Member? member, string text, string channel, bool self) =>
        Json(new { type = "chat", name, job = member?.Job ?? "", role = member?.Role ?? 0, text, channel, self, time = Now });

    public static string Said(long id, string? reason) =>
        Json(new { type = "said", id, ok = reason == null, reason = reason ?? "" });

    public static string Icons(string version, int size, IReadOnlyDictionary<string, string> icons) =>
        Json(new { type = "icons", version, size, icons });

    public static string History(IEnumerable<string> items, bool reset) =>
        $"{{\"type\":\"history\",\"reset\":{(reset ? "true" : "false")},\"items\":[{string.Join(',', items)}]}}";

    private static long Now => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private static string Json(object value) => JsonSerializer.Serialize(value, Options);
}
