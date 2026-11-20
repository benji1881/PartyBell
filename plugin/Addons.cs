using FFXIVClientStructs.FFXIV.Component.GUI;

namespace PartyBell;

internal static unsafe class Addons
{
    public static AtkUnitBase* Ready(string name)
    {
        var addon = (AtkUnitBase*)Services.GameGui.GetAddonByName(name, 1).Address;
        return addon != null && addon->IsReady && addon->IsVisible ? addon : null;
    }

    public static void Centre(AtkUnitBase* addon)
    {
        var stage = AtkStage.Instance();
        if (stage == null)
            return;
        ushort width, height;
        addon->GetSize(&width, &height, true);
        addon->SetPosition((short)((stage->ScreenSize.Width - width) / 2), (short)((stage->ScreenSize.Height - height) / 2));
    }

    public static bool Click(AtkUnitBase* addon, AtkComponentButton* button)
    {
        if (button == null || button->AtkComponentBase.OwnerNode == null)
            return false;
        for (var evt = button->AtkComponentBase.OwnerNode->AtkResNode.AtkEventManager.Event; evt != null; evt = evt->NextEvent)
        {
            if (evt->State.EventType != AtkEventType.ButtonClick)
                continue;
            var data = new AtkEventData();
            addon->ReceiveEvent(AtkEventType.ButtonClick, (int)evt->Param, evt, &data);
            return true;
        }
        return false;
    }
}
