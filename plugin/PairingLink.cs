using System.Collections.Generic;

namespace PartyBell;

internal static class PairingLink
{
    public static string Create(PairedPhone phone, IEnumerable<string> hosts, int port) =>
        $"partybell://pair?hosts={string.Join(',', hosts)}&port={port}&id={phone.Id}&key={phone.Secret}";
}
