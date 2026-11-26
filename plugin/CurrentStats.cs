using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Reads the player's current substats and works out their GCD.
internal static unsafe class CurrentStats
{
    internal const uint Vitality = 3;
    private const uint Intelligence = 4;
    private const uint Mind = 5;
    internal const uint Piety = 6;
    internal const uint Tenacity = 19;
    internal const uint DirectHit = 22;
    internal const uint Critical = 27;
    internal const uint Determination = 44;
    internal const uint SkillSpeed = 45;
    internal const uint SpellSpeed = 46;
    private const byte Tank = 1;
    private const byte Healer = 4;
    private const int BaseGcd = 2500;
    private const int SpeedScale = 130;

    public static List<BisStat> Read()
    {
        var job = Services.PlayerState.ClassJob.Value;
        return new[] { job.PrimaryStat, Vitality }.Concat(Substats(job)).Select(s => new BisStat(ItemStats.Name(s), Attribute(s))).ToList();
    }

    public static List<uint> Substats(Sheets.ClassJob job)
    {
        var stats = new List<uint> { Critical, DirectHit, Determination, Speed(job) };
        if (job.Role == Tank)
            stats.Add(Tenacity);
        if (job.Role == Healer)
            stats.Add(Piety);
        return stats;
    }

    public static double Gcd()
    {
        var player = PlayerState.Instance();
        return player == null ? 0 : Gcd(Attribute(Speed(Services.PlayerState.ClassJob.Value)), (uint)player->CurrentLevel);
    }

    public static double Gcd(int speed, uint level)
    {
        if (Services.DataManager.GetExcelSheet<Sheets.ParamGrow>().GetRowOrDefault(level) is not { } grow || grow.LevelModifier == 0)
            return 0;
        var cut = SpeedScale * (speed - (int)grow.BaseSpeed) / (int)grow.LevelModifier;
        return BaseGcd * (1000 - cut) / 10000 / 100.0;
    }

    public static uint Speed(Sheets.ClassJob job) => (uint)job.PrimaryStat is Intelligence or Mind ? SpellSpeed : SkillSpeed;

    private static int Attribute(uint param)
    {
        var player = PlayerState.Instance();
        return player == null || param >= player->Attributes.Length ? 0 : player->Attributes[(int)param];
    }
}
