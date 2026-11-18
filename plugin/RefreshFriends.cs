namespace PartyBell;

internal sealed record RefreshFriends
{
    public static RefreshFriends? Parse(byte[] json) => Request.Parse(json, "friendsrefresh", _ => new RefreshFriends());
}
