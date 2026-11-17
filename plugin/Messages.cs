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

    public static string Chat(string name, Member? member, string text, string channel, bool self, string peer) =>
        Json(new { type = "chat", name, job = member?.Job ?? "", role = member?.Role ?? 0, text, channel, self, peer, time = Now });

    public static string Said(long id, string? reason) =>
        Json(new { type = "said", id, ok = reason == null, reason = reason ?? "" });

    public static string ReadyCheck(bool answerable) =>
        Json(new { type = "event", kind = "readycheck", name = "", job = "", role = 0, answerable, time = Now });

    public static string ReadyCheckDone(int notReady) =>
        Json(new { type = "event", kind = "readycheckdone", name = "", job = "", role = 0, count = notReady, time = Now });

    public static string Queue(string duty) =>
        Json(new { type = "event", kind = "queue", name = "", job = "", role = 0, detail = duty, time = Now });

    public static string Friends(IReadOnlyList<Friend>? friends) =>
        Json(new { type = "friends", loaded = friends != null, friends = friends ?? [] });

    public static string Bye() => Json(new { type = "bye" });

    public static string Company(bool member) =>
        Json(new { type = "company", member });

    public static string Invited(long id, string? reason) =>
        Json(new { type = "invited", id, ok = reason == null, reason = reason ?? "" });

    public static string Answered(bool ready, string? reason) =>
        Json(new { type = "answered", ready, ok = reason == null, reason = reason ?? "" });

    public static string Dismiss(string kind) =>
        Json(new { type = "dismiss", kind });

    public static string Icons(string version, int size, IReadOnlyDictionary<string, string> icons) =>
        Json(new { type = "icons", version, size, icons });

    public static string History(IEnumerable<string> items, bool reset, bool all = false) =>
        $"{{\"type\":\"history\",\"reset\":{(reset ? "true" : "false")},\"all\":{(all ? "true" : "false")},\"items\":[{string.Join(',', items)}]}}";

    private static long Now => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private static string Json(object value) => JsonSerializer.Serialize(value, Options);
}
