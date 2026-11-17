using System.Linq;
using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.UI.Info;

namespace PartyBell;

internal sealed class Inviter(Configuration config, PartyTracker tracker)
{
    public Task<string?> Send(Invite invite) => config.AllowPhoneInvite
        ? Services.Framework.RunOnFrameworkThread(() => Post(invite))
        : Task.FromResult<string?>("disabled");

    private unsafe string? Post(Invite invite)
    {
        if (!Services.PlayerState.IsLoaded)
            return "not ready";

        var party = tracker.Current;
        if (party is { Members.Count: > 1 })
        {
            if (party.Members.Count >= PartyReader.GroupSize)
                return "party full";
            if (!party.Members.Any(m => m.Leader && m.Name == Services.PlayerState.CharacterName))
                return "not leader";
        }

        var friend = FriendList.Find(invite.Name, invite.World);
        if (friend == null)
            return "not found";
        if (!FriendList.IsOnline(friend->State))
            return "offline";

        var proxy = InfoProxyPartyInvite.Instance();
        var sent = friend->CurrentWorld == Services.PlayerState.CurrentWorld.RowId
            ? proxy->InviteToParty(friend->ContentId, invite.Name, friend->HomeWorld)
            : proxy->InviteToPartyContentId(friend->ContentId, friend->HomeWorld);
        return sent ? null : "failed";
    }
}
