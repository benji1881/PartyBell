using Dalamud.Game.ClientState.Conditions;

namespace PartyBell;

internal static class GameState
{
    public static bool NotReady() =>
        !Services.PlayerState.IsLoaded || Services.Condition[ConditionFlag.BetweenAreas] || Services.Condition[ConditionFlag.BetweenAreas51]
        || Services.Condition[ConditionFlag.WatchingCutscene] || Services.Condition[ConditionFlag.WatchingCutscene78] || Services.Condition[ConditionFlag.OccupiedInCutSceneEvent];
}
