using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace PartyBell;

internal sealed record NetworkAddress(string Address, string Label, bool Tailscale);

internal static class Network
{
    public static IReadOnlyList<NetworkAddress> Addresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up && nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(AddressesOf)
            .OrderBy(a => a.Tailscale)
            .ToList();

    private static IEnumerable<NetworkAddress> AddressesOf(NetworkInterface nic)
    {
        var properties = nic.GetIPProperties();
        var gateway = properties.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork && !g.Address.Equals(IPAddress.Any));
        return properties.UnicastAddresses
            .Select(a => a.Address)
            .Where(a => a.AddressFamily == AddressFamily.InterNetwork)
            .Select(a => IsTailscale(a) ? new NetworkAddress(a.ToString(), "Tailscale", true) : new NetworkAddress(a.ToString(), nic.Name, false))
            .Where(a => a.Tailscale || gateway);
    }

    private static bool IsTailscale(IPAddress address) =>
        address.GetAddressBytes() is [100, var second, _, _] && second is >= 64 and <= 127;
}
