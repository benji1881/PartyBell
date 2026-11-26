using System.Linq;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Which jobs a Party Finder slot set up from the phone accepts.
internal static class RecruitSlot
{
    public const byte Any = 0;
    private const byte Tank = 1;
    private const byte Dps = 2;
    private const byte Healer = 4;
    private const byte RegenHealer = 5;
    private const byte ShieldHealer = 6;
    private const byte Melee = 7;
    private const byte PhysicalRanged = 8;
    private const byte Caster = 9;
    private const byte TankRole = 1;
    private const byte MeleeRole = 2;
    private const byte RangedRole = 3;
    private const byte HealerRole = 4;
    private const byte Intelligence = 4;
    private static readonly uint[] ShieldJobs = [28, 40];

    public static readonly byte[] All = [Any, Tank, Dps, Healer, RegenHealer, ShieldHealer, Melee, PhysicalRanged, Caster];

    public static bool Fits(Sheets.ClassJob job, byte slot) => slot switch
    {
        Any => job.Role != 0,
        Tank => job.Role == TankRole,
        Healer => job.Role == HealerRole,
        RegenHealer => job.Role == HealerRole && !ShieldJobs.Contains(job.RowId),
        ShieldHealer => job.Role == HealerRole && ShieldJobs.Contains(job.RowId),
        Dps => job.Role is MeleeRole or RangedRole,
        Melee => job.Role == MeleeRole,
        PhysicalRanged => job.Role == RangedRole && job.PrimaryStat != Intelligence,
        Caster => job.Role == RangedRole && job.PrimaryStat == Intelligence,
        _ => false,
    };
}
