using System.Collections.Generic;

namespace PartyBell;

internal sealed class Feed
{
    private const int HistorySize = 50;

    private readonly object gate = new();
    private readonly Queue<string> history = new();
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
            history.Enqueue(json);
            if (history.Count > HistorySize)
                history.Dequeue();
            Send(json);
        }
    }

    public void Join(PhoneClient client)
    {
        lock (gate)
        {
            client.Send(party);
            client.Send(Messages.History(history));
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
