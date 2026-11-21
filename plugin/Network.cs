using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace PartyBell;

internal sealed record NetworkAddress(string Address, string Label, bool Tunnel);

internal static class Network
{
    private sealed record Assigned(IPAddress Address, string Nic, bool Gateway, bool Tunnel);

    // The addresses a phone can reach this PC on, home network first.
    public static IReadOnlyList<NetworkAddress> Addresses() =>
        AssignedAddresses()
            .Where(a => a.Tunnel || a.Gateway)
            .Select(a => new NetworkAddress(a.Address.ToString(), a.Tunnel ? "Secure tunnel" : a.Nic, a.Tunnel))
            .OrderBy(a => a.Tunnel)
            .ToList();

    // A tunnel address is only trusted when it arrives on this PC's own tunnel, so someone sharing an ISP's carrier-grade NAT range isn't.
    public static bool IsLocal(IPAddress remote, IPAddress local) =>
        InTunnelRange(remote) ? AssignedAddresses().Any(a => a.Tunnel && a.Address.Equals(local)) : IsHome(remote);

    public static bool IsHome(IPAddress address) =>
        address.GetAddressBytes() is [10, ..] or [127, ..] or [172, >= 16 and <= 31, ..] or [192, 168, ..] or [169, 254, ..];

    // Tailscale gives the PC an address in 100.64.0.0/10 on an adapter with no gateway. An ISP's address in that range comes with one.
    private static IEnumerable<Assigned> AssignedAddresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up && nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(AssignedTo);

    private static IEnumerable<Assigned> AssignedTo(NetworkInterface nic)
    {
        var properties = nic.GetIPProperties();
        var gateway = properties.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork && !g.Address.Equals(IPAddress.Any));
        return properties.UnicastAddresses
            .Select(a => a.Address)
            .Where(a => a.AddressFamily == AddressFamily.InterNetwork)
            .Select(a => new Assigned(a, nic.Name, gateway, !gateway && InTunnelRange(a)));
    }

    private static bool InTunnelRange(IPAddress address) =>
        address.GetAddressBytes() is [100, var second, _, _] && second is >= 64 and <= 127;
}
