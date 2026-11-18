using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace PartyBell;

internal sealed class Feed
{
    private const int HistorySize = 50;
    private const int KeptSize = 200;

    private readonly object gate = new();
    private readonly Queue<(DateTime Time, string Json)> history = new();
    private readonly Queue<(DateTime Time, string Json)> kept = new();
    private readonly List<PhoneClient> clients = [];
    private string party = Messages.Party(PartySnapshot.Empty);
    private string friends = Messages.Friends(null);
    private string company = Messages.Company(false);
    private readonly Dictionary<int, string> listings = [];

    public int Count
    {
        get
        {
            lock (gate)
                return clients.Count;
        }
    }

    public void SetParty(string json)
    {
        lock (gate)
        {
            party = json;
            Send(json);
        }
    }

    public void SetFriends(string json) => Replace(ref friends, json);

    public void SetCompany(string json) => Replace(ref company, json);

    public void SetListings(int category, string json)
    {
        lock (gate)
        {
            listings[category] = json;
            Send(json);
        }
    }

    public void Publish(string json, bool keep = false)
    {
        lock (gate)
        {
            var queue = keep ? kept : history;
            queue.Enqueue((DateTime.UtcNow, json));
            if (queue.Count > (keep ? KeptSize : HistorySize))
                queue.Dequeue();
            Send(json);
        }
    }

    public void Broadcast(string json)
    {
        lock (gate)
            Send(json);
    }

    public void ClearBefore(DateTime time)
    {
        lock (gate)
        {
            var recent = history.Where(h => h.Time >= time).ToList();
            history.Clear();
            foreach (var item in recent)
                history.Enqueue(item);
            Send(Messages.History(All(), true));
        }
    }

    public void ClearAll()
    {
        lock (gate)
        {
            history.Clear();
            kept.Clear();
            Send(Messages.History([], true, true));
            listings.Clear();
            Send(Messages.NoListings());
        }
    }

    public void Join(PhoneClient client)
    {
        lock (gate)
        {
            client.Send(party);
            client.Send(friends);
            client.Send(company);
            client.Send(Messages.NoListings());
            foreach (var json in listings.Values)
                client.Send(json);
            client.Send(Messages.History(All(), false));
            clients.Add(client);
        }
    }

    public Task Goodbye()
    {
        lock (gate)
            return Task.WhenAll(clients.Select(c => c.Goodbye()));
    }

    public void Leave(PhoneClient client)
    {
        lock (gate)
            clients.Remove(client);
    }

    private void Replace(ref string current, string json)
    {
        lock (gate)
        {
            if (json == current)
                return;
            current = json;
            Send(json);
        }
    }

    private IEnumerable<string> All() => history.Concat(kept).OrderBy(h => h.Time).Select(h => h.Json);

    private void Send(string json)
    {
        foreach (var client in clients)
            client.Send(json);
    }
}
