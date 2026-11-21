using System.Linq;
using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.UI.Info;

namespace PartyBell;

internal sealed class Inviter(Configuration config, PartyTracker tracker)
{
    public Task<string?> Send(Invite invite) => PhoneGate.Run(config.AllowPhoneInvite, () => Post(invite));

    private unsafe string? Post(Invite invite)
    {
        if (!Services.PlayerState.IsLoaded)
            return Reason.NotReady;

        var party = tracker.Current;
        if (party is { Members.Count: > 1 })
        {
            if (party.Members.Count >= PartyReader.GroupSize)
                return Reason.PartyFull;
            if (!party.Members.Any(m => m.Leader && m.Name == Services.PlayerState.CharacterName))
                return Reason.NotLeader;
        }

        var friend = FriendList.Find(invite.Name, invite.World);
        if (friend == null)
            return Reason.NotFound;
        if (!FriendList.IsOnline(friend->State))
            return Reason.Offline;

        var proxy = InfoProxyPartyInvite.Instance();
        var sent = friend->CurrentWorld == Services.PlayerState.CurrentWorld.RowId
            ? proxy->InviteToParty(friend->ContentId, invite.Name, friend->HomeWorld)
            : proxy->InviteToPartyContentId(friend->ContentId, friend->HomeWorld);
        return sent ? null : Reason.Failed;
    }
}
