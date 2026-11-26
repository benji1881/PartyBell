using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using FFXIVClientStructs.FFXIV.Client.UI;
using FFXIVClientStructs.FFXIV.Client.UI.Agent;
using FFXIVClientStructs.FFXIV.Component.GUI;
using Lumina.Text.ReadOnly;
using static FFXIVClientStructs.FFXIV.Client.UI.Agent.AgentLookingForGroup;
using JobFlags = Dalamud.Game.Gui.PartyFinder.Types.JobFlags;
using JobFlagsExtensions = Dalamud.Game.Gui.PartyFinder.Types.JobFlagsExtensions;
using Sheets = Lumina.Excel.Sheets;

namespace PartyBell;

// Posts a Party Finder listing for the phone by filling in and sending the game's recruitment window.
internal sealed unsafe class Recruiter(Feed feed, Configuration config, PartyTracker tracker)
{
    private const string Finder = "LookingForGroup";
    private const string Criteria = "LookingForGroupCondition";
    private const uint RecruitButton = 46;
    private const byte DataCenter = 0;
    private const ushort NoPassword = 10000;
    private const int Yes = 0;
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(10);

    private enum Stage { Finder, Criteria, Post, Confirm }

    private List<(ulong Flag, Sheets.ClassJob Job)>? jobs;
    private IReadOnlyList<RecruitDuty> duties = [];
    private TaskCompletionSource<string?>? posting;
    private Recruit request = null!;
    private string dutyName = "";
    private Stage stage;
    private bool listOpened;
    private DateTime by;
    private bool open;
    private bool? shown;

    public Task<string?> Start(Recruit recruit) => PhoneGate.Run(config.AllowPhoneRecruit, () => Begin(recruit));

    public void Update()
    {
        var can = Blocked() == null;
        if (can && !open)
            duties = RecruitDuties.Read();
        open = can;
        var show = open && config.AllowPhoneRecruit;
        if (show == shown)
            return;
        shown = show;
        feed.SetRecruit(Messages.Recruit(show, show ? duties : []));
    }

    public void Step(DateTime now)
    {
        if (posting == null)
            return;
        var agent = AgentLookingForGroup.Instance();
        if (now > by || agent == null)
        {
            Finish(Reason.Timeout);
            return;
        }
        var criteria = (AddonLookingForGroupCondition*)Addons.Ready(Criteria);
        switch (stage)
        {
            case Stage.Finder:
                var finder = Addons.Ready(Finder);
                if (finder == null)
                    return;
                Fill(agent);
                if (Addons.Click(finder, finder->GetComponentButtonById(RecruitButton)))
                    stage = Stage.Criteria;
                return;
            case Stage.Criteria:
                if (criteria != null && PickDuty(criteria))
                    stage = Stage.Post;
                return;
            case Stage.Post:
                var button = criteria == null ? null : criteria->RecruitMembersButton;
                if (button != null && button->IsEnabled && Addons.Click(&criteria->AtkUnitBase, button))
                    stage = Stage.Confirm;
                return;
            case Stage.Confirm:
                var confirm = Addons.Ready(Addons.YesNo);
                if (confirm != null)
                    confirm->FireCallbackInt(Yes);
                else if (agent->OwnListingId != 0 || criteria == null)
                    Finish(null);
                return;
        }
    }

    public void Reset()
    {
        Finish(Reason.NotReady);
        open = false;
        shown = null;
        duties = [];
    }

    private Task<string?> Begin(Recruit recruit)
    {
        var name = recruit.Duty == 0 ? "" : duties.FirstOrDefault(d => d.Id == recruit.Duty && d.Category == recruit.Category && d.Mode == recruit.Mode)?.Name;
        var agent = AgentLookingForGroup.Instance();
        var reason = posting != null ? Reason.Busy
            : !recruit.Valid ? Reason.Invalid
            : recruit.TooLong ? Reason.TooLong
            : Blocked() ?? (Addons.Ready(Criteria) != null ? Reason.WindowOpen : name == null ? Reason.NotFound : agent == null ? Reason.Refused : null);
        if (reason != null)
            return Task.FromResult<string?>(reason);
        agent->SearchAreaTab = DataCenter;
        if (Addons.Ready(Finder) == null)
            agent->Show();
        request = recruit;
        dutyName = name!;
        stage = Stage.Finder;
        listOpened = false;
        by = DateTime.UtcNow + Timeout;
        posting = new TaskCompletionSource<string?>();
        return posting.Task;
    }

    private string? Blocked()
    {
        if (GameState.FinderBlocked() is { } reason)
            return reason;
        if (QueueStatus.Read(false) != null)
            return Reason.Queued;
        if (tracker.Current is { Members.Count: > 1 })
            return Reason.InParty;
        var agent = AgentLookingForGroup.Instance();
        return agent == null ? Reason.NotReady : agent->OwnListingId != 0 ? Reason.Recruiting : null;
    }

    private void Fill(AgentLookingForGroup* agent)
    {
        ref var info = ref agent->StoredRecruitmentInfo;
        info.SelectedCategory = request.Category == ListingMap.Other ? DutyCategory.None : (DutyCategory)(1u << request.Category);
        info.SelectedDutyId = (ushort)request.Duty;
        info.Objective = (Objective)request.Objective;
        info.CompletionStatus = (CompletionStatus)request.Completion;
        info.LootRule = (LootRule)request.Loot;
        info.BeginnerFriendly = (byte)(request.Beginners ? 1 : 0);
        info.OnePlayerPerJob = (byte)(request.OnePerJob ? 1 : 0);
        info.Password = request.Password ?? NoPassword;
        var size = request.Mode switch
        {
            RecruitMode.Alliance => RecruitMode.PartySize,
            RecruitMode.CustomMatch => RecruitMode.TeamSize,
            _ => request.Slots.Count,
        };
        agent->GroupTypeTab = request.Mode;
        var spectators = request.Mode == RecruitMode.CustomMatch && !request.NoSpectators;
        info.NumberOfGroups = (byte)(request.Mode == RecruitMode.Normal ? 1 : request.Mode == RecruitMode.CustomMatch && !spectators ? 2 : RecruitMode.Groups);
        info.NumberOfSlotsInMainParty = (byte)size;
        info.SlotFlags.Clear();
        for (var i = 0; i < request.Slots.Count; i++)
            info.SlotFlags[i / size * RecruitMode.PartySize + i % size] = Mask(request.Slots[i]);
        if (spectators)
            info.SlotFlags.Slice(2 * RecruitMode.PartySize, RecruitMode.PartySize).Fill(Mask(RecruitSlot.Any));
        info.CommentString = request.Comment + "\0";
        agent->AvgItemLv = request.ItemLevel;
        agent->AvgItemLvEnabled = (byte)(request.ItemLevel > 0 ? 1 : 0);
    }

    private bool PickDuty(AddonLookingForGroupCondition* addon)
    {
        if (dutyName.Length == 0)
            return true;
        var drop = addon->DutyDropDown;
        if (drop == null || drop->List == null)
            return false;
        var index = Find(drop->List, dutyName);
        if (index < 0)
        {
            if (listOpened)
                Finish(Reason.NotFound);
            else
                listOpened = Toggle(addon, drop);
            return false;
        }
        if (drop->GetSelectedItemIndex() != index)
        {
            drop->SelectItem(index);
            drop->List->SelectItem(index, true);
        }
        if (drop->IsOpen)
            Toggle(addon, drop);
        return true;
    }

    private static bool Toggle(AddonLookingForGroupCondition* addon, AtkComponentDropDownList* drop) =>
        drop->Checkbox != null && Addons.Click(&addon->AtkUnitBase, &drop->Checkbox->AtkComponentButton);

    private static int Find(AtkComponentList* list, string name)
    {
        for (var i = 0; i < list->GetItemCount(); i++)
        {
            if (string.Equals(new ReadOnlySeStringSpan(list->GetItemLabel(i).AsSpan()).ExtractText(), name, StringComparison.OrdinalIgnoreCase))
                return i;
        }
        return -1;
    }

    private ulong Mask(byte slot)
    {
        jobs ??= Enum.GetValues<JobFlags>()
            .Select(flag => (Flag: (ulong)flag, Job: JobFlagsExtensions.ClassJob(flag, Services.DataManager)))
            .Where(job => job.Job != null)
            .Select(job => (job.Flag, job.Job!.Value))
            .ToList();
        return jobs.Where(job => RecruitSlot.Fits(job.Job, slot)).Aggregate(0UL, (all, job) => all | job.Flag);
    }

    private void Finish(string? reason)
    {
        var task = posting;
        posting = null;
        task?.TrySetResult(reason);
    }
}
