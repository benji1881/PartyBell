using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using Dalamud.Bindings.ImGui;
using Dalamud.Interface.Windowing;

namespace PartyBell;

internal sealed class MainWindow : Window
{
    private const float QrSize = 280;
    private static readonly Vector4 ErrorColor = new(1f, 0.4f, 0.4f, 1f);
    private static readonly Vector4 MutedColor = new(0.7f, 0.7f, 0.7f, 1f);

    private readonly Configuration config;
    private readonly PhoneServer server;
    private readonly Feed feed;
    private IReadOnlyList<NetworkAddress> addresses = [];
    private QrCode? pairing;
    private int port;

    public MainWindow(Configuration config, PhoneServer server, Feed feed) : base("PartyBell")
    {
        this.config = config;
        this.server = server;
        this.feed = feed;
        port = config.Port;
        SizeConstraints = new WindowSizeConstraints { MinimumSize = new Vector2(320, 220) };
    }

    public override void OnOpen() => addresses = Network.Addresses();

    public override void OnClose() => CancelPairing();

    public override void Draw()
    {
        if (pairing != null && server.Pending == null)
            pairing = null;

        if (pairing != null)
            DrawPairing(pairing);
        else
            DrawSettings();
    }

    private void DrawPairing(QrCode qr)
    {
        ImGui.TextWrapped("Scan this with the PartyBell app. Keep it hidden while streaming.");
        ImGui.PushStyleColor(ImGuiCol.Text, MutedColor);
        ImGui.TextWrapped("If Windows asks to let the game use your network, allow it on private networks only.");
        ImGui.PopStyleColor();
        ImGui.Spacing();
        qr.Draw(QrSize);
        ImGui.TextColored(MutedColor, "Waiting for your phone...");
        ImGui.Spacing();
        if (ImGui.Button("Cancel"))
            CancelPairing();
    }

    private void DrawSettings()
    {
        ImGui.TextUnformatted("Address");
        if (addresses.Count == 0)
            ImGui.TextColored(ErrorColor, "No network found.");
        foreach (var address in addresses)
            ImGui.BulletText($"{address.Address}  ({address.Label})");

        ImGui.Spacing();
        ImGui.SetNextItemWidth(120);
        ImGui.InputInt("Port", ref port, 0);
        if (ImGui.IsItemDeactivatedAfterEdit())
            ApplyPort();
        ImGui.TextColored(MutedColor, "Pair your phones again after changing the port.");

        var sendChat = config.SendChat;
        if (ImGui.Checkbox("Send party chat", ref sendChat))
        {
            config.SendChat = sendChat;
            config.Save();
        }

        var sendTells = config.SendTells;
        if (ImGui.Checkbox("Send tells", ref sendTells))
        {
            config.SendTells = sendTells;
            config.Save();
        }

        var sendFreeCompanyChat = config.SendFreeCompanyChat;
        if (ImGui.Checkbox("Send Free Company chat", ref sendFreeCompanyChat))
        {
            config.SendFreeCompanyChat = sendFreeCompanyChat;
            config.Save();
        }

        var sendFriends = config.SendFriends;
        if (ImGui.Checkbox("Send friend list", ref sendFriends))
        {
            config.SendFriends = sendFriends;
            config.Save();
        }

        var allowPhoneChat = config.AllowPhoneChat;
        if (ImGui.Checkbox("Allow writing from phones", ref allowPhoneChat))
        {
            config.AllowPhoneChat = allowPhoneChat;
            config.Save();
        }

        var allowPhoneReadyCheck = config.AllowPhoneReadyCheck;
        if (ImGui.Checkbox("Allow answering ready checks from phones", ref allowPhoneReadyCheck))
        {
            config.AllowPhoneReadyCheck = allowPhoneReadyCheck;
            config.Save();
        }

        var allowPhoneInvite = config.AllowPhoneInvite;
        if (ImGui.Checkbox("Allow party invites from phones", ref allowPhoneInvite))
        {
            config.AllowPhoneInvite = allowPhoneInvite;
            config.Save();
        }

        var allowPhoneJoin = config.AllowPhoneJoin;
        if (ImGui.Checkbox("Allow joining listings from phones", ref allowPhoneJoin))
        {
            config.AllowPhoneJoin = allowPhoneJoin;
            config.Save();
        }

        ImGui.Separator();
        DrawPhones();

        ImGui.Separator();
        if (server.Error is { } error)
            ImGui.TextColored(ErrorColor, $"Port {config.Port} is not available: {error}");
        else
            ImGui.TextUnformatted(feed.Count == 1 ? "1 phone connected" : $"{feed.Count} phones connected");
    }

    private void DrawPhones()
    {
        ImGui.TextUnformatted("Phones");
        if (config.Phones.Count == 0)
            ImGui.TextColored(MutedColor, "No phones paired yet.");

        foreach (var phone in config.Phones)
        {
            ImGui.BulletText(phone.Name);
            ImGui.SameLine();
            if (ImGui.SmallButton($"Forget##{phone.Id}"))
            {
                config.RemovePhone(phone);
                Restart();
                break;
            }
        }

        if (ImGui.Button("Pair a phone"))
            Pair();
    }

    private void Pair()
    {
        addresses = Network.Addresses();
        if (addresses.Count == 0)
            return;
        var phone = PairedPhone.Create();
        server.Pending = phone;
        pairing = new QrCode(PairingLink.Create(phone, addresses.Select(a => a.Address), config.Port));
        if (!server.Running)
            server.Start();
    }

    private void CancelPairing()
    {
        if (pairing == null)
            return;
        pairing = null;
        server.Pending = null;
        if (config.Phones.Count == 0)
            server.Stop();
    }

    private void Restart()
    {
        if (config.Phones.Count > 0)
            server.Start();
        else
            server.Stop();
    }

    private void ApplyPort()
    {
        if (port is < 1024 or > 65535)
        {
            port = config.Port;
            return;
        }
        if (port == config.Port)
            return;

        config.Port = port;
        config.Save();
        Restart();
    }
}
