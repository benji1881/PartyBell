using System.Collections.Generic;
using FFXIVClientStructs.FFXIV.Client.Game.UI;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

internal sealed record Character(
    string Name,
    string World,
    string DataCenter,
    string Job,
    string JobName,
    int Role,
    int Level,
    bool Max,
    long Exp,
    long ExpNeeded,
    int ItemLevel,
    string Zone,
    string Area,
    int Instance,
    IReadOnlyList<GearPiece> Gear,
    IReadOnlyList<Currency> Currencies)
{
    public static unsafe Character? Read()
    {
        var state = Services.PlayerState;
        var player = PlayerState.Instance();
        if (!state.IsLoaded || player == null || state.HomeWorld.ValueNullable is not { } home || state.ClassJob.ValueNullable is not { } job)
            return null;
        var places = Services.DataManager.GetExcelSheet<Sheets.PlaceName>();
        var territory = Services.DataManager.GetExcelSheet<Sheets.TerritoryType>().GetRowOrDefault(Services.ClientState.TerritoryType);
        var info = TerritoryInfo.Instance();
        var area = info == null ? null : places.GetRowOrDefault(info->AreaPlaceNameId);
        var instance = UIState.Instance();
        return new Character(
            state.CharacterName,
            home.Name.ExtractText(),
            home.DataCenter.ValueNullable?.Name.ExtractText() ?? "",
            job.Abbreviation.ExtractText(),
            DutyQueue.Capitalised(job.Name.ExtractText()) ?? "",
            job.Role,
            player->CurrentLevel,
            player->CurrentLevel >= player->GetCurrentClassJobMaxLevel(),
            player->GetCurrentClassJobExp(),
            player->GetCurrentClassJobNeededExp(),
            PartyBell.Gear.ItemLevel(),
            territory?.PlaceName.ValueNullable?.Name.ExtractText() ?? "",
            area?.Name.ExtractText() ?? "",
            instance == null ? 0 : (int)instance->PublicInstance.InstanceId,
            PartyBell.Gear.Read(),
            PartyBell.Currencies.Owned());
    }
}
