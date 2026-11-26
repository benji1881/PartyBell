using System.Collections.Generic;
using System.Linq;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Reads the player's current substats and works out their GCD.
internal static unsafe class CurrentStats
{
    private const uint Vitality = 3;
    private const uint Intelligence = 4;
    private const uint Mind = 5;
    private const uint Piety = 6;
    private const uint Tenacity = 19;
    private const uint DirectHit = 22;
    private const uint Critical = 27;
    private const uint Determination = 44;
    private const uint SkillSpeed = 45;
    private const uint SpellSpeed = 46;
    private const byte Tank = 1;
    private const byte Healer = 4;
    private const int BaseGcd = 2500;
    private const int SpeedScale = 130;

    public static List<BisStat> Read()
    {
        var job = Services.PlayerState.ClassJob.Value;
        var stats = new List<uint> { job.PrimaryStat, Vitality, Critical, DirectHit, Determination, Speed(job) };
        if (job.Role == Tank)
            stats.Add(Tenacity);
        if (job.Role == Healer)
            stats.Add(Piety);
        return stats.Select(s => new BisStat(ItemStats.Name(s), Attribute(s))).ToList();
    }

    public static double Gcd()
    {
        var player = PlayerState.Instance();
        if (player == null || Services.DataManager.GetExcelSheet<Sheets.ParamGrow>().GetRowOrDefault((uint)player->CurrentLevel) is not { } grow || grow.LevelModifier == 0)
            return 0;
        var speed = Attribute(Speed(Services.PlayerState.ClassJob.Value));
        var cut = SpeedScale * (speed - (int)grow.BaseSpeed) / (int)grow.LevelModifier;
        return BaseGcd * (1000 - cut) / 10000 / 100.0;
    }

    private static uint Speed(Sheets.ClassJob job) => (uint)job.PrimaryStat is Intelligence or Mind ? SpellSpeed : SkillSpeed;

    private static int Attribute(uint param)
    {
        var player = PlayerState.Instance();
        return player == null || param >= player->Attributes.Length ? 0 : player->Attributes[(int)param];
    }
}
