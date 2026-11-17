using System;
using System.Collections.Generic;
using Dalamud.Game.ClientState.Party;
using Dalamud.Plugin.Services;
using FFXIVClientStructs.FFXIV.Client.UI.Info;
using Lumina.Excel.Sheets;

namespace PartyBell;

internal static class PartyReader
{
    public const int GroupSize = 8;
    private const int AllianceSize = GroupSize * 3;
    private const int AllianceSlots = 20;

    public static PartySnapshot Read()
    {
        if (Services.PartyList.Length > 0)
            return ReadLocal(Services.PartyList);
        if (InfoProxyCrossRealm.IsCrossRealmParty())
            return ReadCrossRealm();
        return PartySnapshot.Empty;
    }

    private static PartySnapshot ReadLocal(IPartyList party)
    {
        var members = new List<Member>();
        for (var i = 0; i < party.Length; i++)
        {
            if (party[i] is { } member)
                members.Add(FromPartyMember(member, i == party.PartyLeaderIndex, 0));
        }

        if (!party.IsAlliance)
            return new PartySnapshot(members, GroupSize);

        for (var i = 0; i < AllianceSlots; i++)
        {
            var address = party.GetAllianceMemberAddress(i);
            if (address != IntPtr.Zero && party.CreateAllianceMemberReference(address) is { } member && member.Name.TextValue.Length > 0)
                members.Add(FromPartyMember(member, false, 1 + i / GroupSize));
        }
        return new PartySnapshot(members, AllianceSize);
    }

    private static unsafe PartySnapshot ReadCrossRealm()
    {
        var members = new List<Member>();
        var proxy = InfoProxyCrossRealm.Instance();
        var groups = Math.Max(1, (int)proxy->GroupCount);
        var own = proxy->LocalPlayerGroupIndex;
        for (var group = 0; group < groups; group++)
        {
            var count = InfoProxyCrossRealm.GetGroupMemberCount(group);
            for (uint i = 0; i < count; i++)
            {
                var member = InfoProxyCrossRealm.GetGroupMember(i, group);
                if (member != null && member->ContentId != 0)
                    members.Add(Create(member->NameString, (uint)member->HomeWorld, member->ClassJobId, member->Level, member->IsPartyLeader, OwnFirst(member->GroupIndex, own)));
            }
        }
        return new PartySnapshot(members, InfoProxyCrossRealm.IsAllianceRaid() ? AllianceSize : GroupSize);
    }

    public static Member Self()
    {
        var player = Services.PlayerState;
        return Create(player.CharacterName, player.HomeWorld.RowId, player.ClassJob.RowId, player.Level, false, 0);
    }

    private static int OwnFirst(int group, int own) => group == own ? 0 : group < own ? group + 1 : group;

    private static Member FromPartyMember(IPartyMember member, bool leader, int group) =>
        Create(member.Name.TextValue, member.World.RowId, member.ClassJob.RowId, member.Level, leader, group);

    private static Member Create(string name, uint worldId, uint jobId, int level, bool leader, int group)
    {
        var job = Services.DataManager.GetExcelSheet<ClassJob>().GetRowOrDefault(jobId);
        var world = Services.DataManager.GetExcelSheet<World>().GetRowOrDefault(worldId);
        return new Member(
            name,
            world?.Name.ExtractText() ?? "",
            job?.Abbreviation.ExtractText() ?? "",
            job?.Role ?? 0,
            level,
            leader,
            group);
    }
}
