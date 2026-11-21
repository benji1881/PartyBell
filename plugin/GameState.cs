using Dalamud.Game.ClientState.Conditions;

namespace PartyBell;

internal static class GameState
{
    public static bool BetweenAreas => Services.Condition[ConditionFlag.BetweenAreas] || Services.Condition[ConditionFlag.BetweenAreas51];

    public static bool InDuty => Services.Condition[ConditionFlag.BoundByDuty] || Services.Condition[ConditionFlag.BoundByDuty56] || Services.Condition[ConditionFlag.BoundByDuty95];

    // Why the Party Finder can't be used right now, or null when it can.
    public static string? FinderBlocked() => NotReady() ? Reason.NotReady : InDuty ? Reason.InDuty : null;

    public static bool NotReady() =>
        !Services.PlayerState.IsLoaded || BetweenAreas
        || Services.Condition[ConditionFlag.WatchingCutscene] || Services.Condition[ConditionFlag.WatchingCutscene78] || Services.Condition[ConditionFlag.OccupiedInCutSceneEvent];
}
