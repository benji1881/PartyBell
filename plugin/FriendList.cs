using System.Collections.Generic;
using FFXIVClientStructs.FFXIV.Client.UI.Info;
using Lumina.Excel.Sheets;
using Status = FFXIVClientStructs.FFXIV.Client.UI.Info.InfoProxyCommonList.CharacterData.OnlineStatus;

namespace PartyBell;

internal static unsafe class FriendList
{
    public const string Online = "online";
    public const string Offline = "offline";
    private const Status OfflineStates = Status.Offline | Status.OfflineExd | Status.Disconnected;
    private const Status DutyStates = Status.InDuty | Status.SharingDuty | Status.SimilarDuty | Status.PvP;

    public static List<Friend>? Read()
    {
        var proxy = InfoProxyFriendList.Instance();
        if (proxy == null)
            return null;
        var count = proxy->GetEntryCount();
        if (count == 0)
            return null;

        var friends = new List<Friend>();
        for (uint i = 0; i < count; i++)
        {
            var entry = proxy->GetEntry(i);
            if (entry == null || entry->ContentId == 0 || entry->WaitingForFriendListApproval)
                continue;
            var member = PartyReader.Create(entry->NameString, entry->HomeWorld, entry->Job, 0, false, 0);
            friends.Add(new Friend(member.Name, member.World, member.Job, member.Role, Describe(entry->State)));
        }
        return friends;
    }

    public static InfoProxyCommonList.CharacterData* Find(string name, string world)
    {
        var proxy = InfoProxyFriendList.Instance();
        if (proxy == null)
            return null;
        for (uint i = 0; i < proxy->GetEntryCount(); i++)
        {
            var entry = proxy->GetEntry(i);
            if (entry != null && entry->NameString == name && WorldName(entry->HomeWorld) == world)
                return entry;
        }
        return null;
    }

    public static bool IsOnline(Status state) => Describe(state) != Offline;

    private static string Describe(Status state) => state switch
    {
        0 => Offline,
        _ when (state & OfflineStates) != 0 => Offline,
        _ when (state & DutyStates) != 0 => "duty",
        _ when (state & Status.AwayFromKeyboard) != 0 => "away",
        _ when (state & Status.Busy) != 0 => "busy",
        _ => Online,
    };

    private static string WorldName(uint id) =>
        Services.DataManager.GetExcelSheet<World>().GetRowOrDefault(id)?.Name.ExtractText() ?? "";
}
