using FFXIVClientStructs.FFXIV.Client.UI.Info;

namespace PartyBell;

internal static unsafe class Company
{
    public static bool IsMember
    {
        get
        {
            var proxy = InfoProxyFreeCompany.Instance();
            return proxy != null && proxy->Id != 0;
        }
    }
}
