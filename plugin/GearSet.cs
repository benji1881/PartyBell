using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed record GearSet(uint Job, string Name, IReadOnlyDictionary<int, uint> Slots, uint Food, IReadOnlyDictionary<uint, int> Materia);

// Picks the site a saved best in slot link belongs to: a bare id is Etro, a full link is xivgear.
internal static class GearSets
{
    private static readonly HttpClient Http = CreateClient();

    public static string? Key(string link) => Etro.Id(link) ?? XivGear.Key(link);

    public static string Link(string key) => XivGear.Owns(key) ? key : Etro.Link(key);

    public static Task<GearSet?> Fetch(string key) => XivGear.Owns(key) ? XivGear.Fetch(key) : Etro.Fetch(key);

    public static async Task<JsonDocument?> Get(string url)
    {
        using var response = await Http.GetAsync(url);
        return response.IsSuccessStatusCode ? JsonDocument.Parse(await response.Content.ReadAsStringAsync()) : null;
    }

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("PartyBell");
        return client;
    }
}
