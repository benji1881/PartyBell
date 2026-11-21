using System.Text.Json.Serialization;

namespace PartyBell;

public sealed record Member(string Name, string World, string Job, int Role, int Level, bool Leader, int Group)
{
    [JsonIgnore]
    public string Key => KeyOf(Name, World);

    public static string KeyOf(string name, string world) => $"{name}@{world}";
}
