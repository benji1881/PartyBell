using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace PartyBell;

internal static class Messages
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web);

    public static string Party(PartySnapshot party) =>
        Json(new { type = "party", members = party.Members, capacity = party.Capacity });

    public static string Event(PartyEvent e) =>
        Json(new { type = "event", kind = e.Kind, name = e.Member?.Name ?? "", job = e.Member?.Job ?? "", role = e.Member?.Role ?? 0, time = Now });

    public static string Chat(string name, Member? member, ChatLink text, string channel, bool self, string peer) =>
        Json(new { type = "chat", name, job = member?.Job ?? "", role = member?.Role ?? 0, text = text.Text, listing = text.Listing, link = text.Link, channel, self, peer, time = Now });

    public static string ReadyCheck(bool answerable) =>
        Json(new { type = "event", kind = AlertKind.ReadyCheck, answerable, time = Now });

    public static string ReadyCheckDone(int notReady) =>
        Json(new { type = "event", kind = "readycheckdone", count = notReady, time = Now });

    public static string Queue(string duty) =>
        Json(new { type = "event", kind = AlertKind.Queue, detail = duty, time = Now });

    public static string QueueStatus(QueueStatus? status) =>
        Json(new { type = "queuestatus", status });

    public static string Character(Character? character) =>
        Json(new { type = "character", character });

    public static string ItemIcons(IconSet icons) =>
        Json(new { type = "itemicons", icons.Size, icons.Icons });

    public static string Friends(IReadOnlyList<Friend>? friends) =>
        Json(new { type = "friends", loaded = friends != null, friends = friends ?? [] });

    public static string Listings(int category, IReadOnlyList<Listing> listings) =>
        Json(new { type = "listings", category, time = Now, listings });

    public static string NoListings() =>
        Json(new { type = "listings", category = 0, time = 0, listings = Array.Empty<Listing>() });

    public static string Plugin(string version) => Json(new { type = "plugin", version });

    public static string Bye() => Json(new { type = "bye" });

    public static string Company(bool member) =>
        Json(new { type = "company", member });

    public static string Dismiss(string kind) =>
        Json(new { type = "dismiss", kind });

    public static string Icons(string version, int size, IReadOnlyDictionary<string, string> icons, int roleSize, IReadOnlyDictionary<string, string> roles, IReadOnlyList<Job> jobs, int sproutSize, string sprout, IconSet symbols, IconSet currencies) =>
        Json(new { type = "icons", version, size, icons, roleSize, roles, jobs, sproutSize, sprout, symbols, currencies });

    public static string History(IEnumerable<string> items, bool reset, bool all = false) =>
        $"{{\"type\":\"history\",\"reset\":{(reset ? "true" : "false")},\"all\":{(all ? "true" : "false")},\"items\":[{string.Join(',', items)}]}}";

    public static string Said(long id, string? reason, string error) => Reply("said", reason, new { id, error });

    public static string Invited(long id, string? reason) => Reply("invited", reason, new { id });

    public static string Answered(bool ready, string? reason) => Reply("answered", reason, new { ready });

    public static string Duty(string action, string? reason) => Reply("duty", reason, new { action });

    public static string Finding(string? reason) => Reply("finding", reason);

    public static string Refreshing(string? reason) => Reply("refreshing", reason);

    public static string Opened(string? reason) => Reply("opened", reason);

    public static string Joining(string? reason) => Reply("joining", reason);

    public static string Recruit(bool open, IReadOnlyList<RecruitDuty> duties) =>
        Json(new { type = "recruit", open, duties });

    public static string Recruiting(string? reason) => Reply("recruiting", reason);

    public static string Bis(string? link, BisView? set) => Json(new { type = "bis", link, set });

    public static string BisLinked(string? reason) => Reply("bislinked", reason);

    // The answer to a phone request: ok, or the reason it failed, plus any fields that say which request it was.
    private static string Reply(string type, string? reason, object? fields = null)
    {
        var reply = fields == null ? new JsonObject() : JsonSerializer.SerializeToNode(fields, Options)!.AsObject();
        reply["type"] = type;
        reply["ok"] = reason == null;
        reply["reason"] = reason ?? "";
        return reply.ToJsonString(Options);
    }

    private static long Now => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private static string Json(object value) => JsonSerializer.Serialize(value, Options);
}

// Alerts the phone shows and is later told to take down.
internal static class AlertKind
{
    public const string ReadyCheck = "readycheck";
    public const string Queue = "queue";
}
