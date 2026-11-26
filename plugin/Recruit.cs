using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;

namespace PartyBell;

internal sealed record Recruit(byte Category, uint Duty, byte Objective, byte Completion, byte Loot, IReadOnlyList<byte> Slots, ushort ItemLevel, ushort? Password, bool Beginners, bool OnePerJob, string Comment)
{
    public const string Type = "recruit";
    public const int MaxComment = 191;
    private const int MaxSlots = 8;
    private const ushort MaxItemLevel = 999;
    private const ushort MaxPassword = 9999;
    private static readonly byte[] Flags = [1, 2, 4, 8];
    private static readonly byte[] Roles = [0, 1, 2, 4];

    public bool Valid =>
        RecruitDuties.Categories.Contains(Category)
        && (Duty != 0 || !RecruitDuties.NeedsDuty(Category))
        && Flags.Contains(Objective) && Flags.Contains(Completion) && Loot <= 2
        && Slots.Count is > 0 and <= MaxSlots && Slots.All(Roles.Contains)
        && ItemLevel <= MaxItemLevel && Password is null or <= MaxPassword;

    public bool TooLong => Encoding.UTF8.GetByteCount(Comment) > MaxComment;

    public static Recruit Read(JsonElement root) => new(
        root.GetProperty("category").GetByte(),
        root.GetProperty("duty").GetUInt32(),
        root.GetProperty("objective").GetByte(),
        root.GetProperty("completion").GetByte(),
        root.GetProperty("loot").GetByte(),
        root.GetProperty("slots").EnumerateArray().Select(s => s.GetByte()).ToList(),
        root.GetProperty("itemLevel").GetUInt16(),
        root.TryGetProperty("password", out var password) && password.ValueKind == JsonValueKind.Number ? password.GetUInt16() : null,
        root.GetProperty("beginners").GetBoolean(),
        root.GetProperty("onePerJob").GetBoolean(),
        root.GetProperty("comment").GetString() ?? "");
}
