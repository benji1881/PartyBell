using System.Collections.Generic;
using System.Linq;
using Dalamud.Configuration;

namespace PartyBell;

public sealed class Configuration : IPluginConfiguration
{
    public int Version { get; set; }
    public int Port { get; set; } = 4747;
    public bool SendChat { get; set; } = true;
    public bool AllowPhoneChat { get; set; } = true;
    public bool AllowPhoneReadyCheck { get; set; } = true;
    public bool SendTells { get; set; } = true;
    public bool SendFreeCompanyChat { get; set; } = true;
    public bool SendFriends { get; set; } = true;
    public bool AllowPhoneInvite { get; set; } = true;
    public List<PairedPhone> Phones { get; set; } = [];

    public void AddPhone(PairedPhone phone)
    {
        Phones = [.. Phones, phone];
        Save();
    }

    public void RemovePhone(PairedPhone phone)
    {
        Phones = Phones.Where(p => p != phone).ToList();
        Save();
    }

    public void Save() => Services.PluginInterface.SavePluginConfig(this);
}
