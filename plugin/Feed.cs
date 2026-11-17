using System;
using System.Collections.Generic;
using System.Linq;

namespace PartyBell;

internal sealed class Feed
{
    private const int HistorySize = 50;

    private readonly object gate = new();
    private readonly Queue<(DateTime Time, string Json)> history = new();
    private readonly List<PhoneClient> clients = [];
    private string party = Messages.Party(PartySnapshot.Empty);

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

    public void Publish(string json)
    {
        lock (gate)
        {
            history.Enqueue((DateTime.UtcNow, json));
            if (history.Count > HistorySize)
                history.Dequeue();
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
            var kept = history.Where(h => h.Time >= time).ToList();
            history.Clear();
            foreach (var item in kept)
                history.Enqueue(item);
            Send(Messages.History(kept.Select(h => h.Json), true));
        }
    }

    public void Join(PhoneClient client)
    {
        lock (gate)
        {
            client.Send(party);
            client.Send(Messages.History(history.Select(h => h.Json), false));
            clients.Add(client);
        }
    }

    public void Leave(PhoneClient client)
    {
        lock (gate)
            clients.Remove(client);
    }

    private void Send(string json)
    {
        foreach (var client in clients)
            client.Send(json);
    }
}
