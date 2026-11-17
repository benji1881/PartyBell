using System.Collections.Generic;
using System.Linq;
using Dalamud.Configuration;

namespace PartyBell;

public sealed class Configuration : IPluginConfiguration
{
    public int Version { get; set; }
    public int Port { get; set; } = 4747;
    public bool SendChat { get; set; } = true;
    public List<PairedPhone> Phones { get; set; } = [];

    public PairedPhone AddPhone()
    {
        var phone = PairedPhone.Create();
        Phones = [.. Phones, phone];
        Save();
        return phone;
    }

    public void RemovePhone(PairedPhone phone)
    {
        Phones = Phones.Where(p => p != phone).ToList();
        Save();
    }

    public void Save() => Services.PluginInterface.SavePluginConfig(this);
}
