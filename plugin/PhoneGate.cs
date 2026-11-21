using System;
using System.Threading.Tasks;

namespace PartyBell;

// Runs a phone request on the game thread, or turns it down when its setting is off.
internal static class PhoneGate
{
    public static Task<string?> Run(bool allowed, Func<string?> action) =>
        allowed ? Services.Framework.RunOnFrameworkThread(action) : Task.FromResult<string?>(Reason.Disabled);

    public static Task<string?> Run(bool allowed, Func<Task<string?>> action) =>
        allowed ? Services.Framework.RunOnFrameworkThread<Task<string?>>(action).Unwrap() : Task.FromResult<string?>(Reason.Disabled);
}
