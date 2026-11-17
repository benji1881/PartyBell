using System.Collections;
using System.Collections.Generic;
using System.Numerics;
using Dalamud.Bindings.ImGui;
using QRCoder;

namespace PartyBell;

internal sealed class QrCode
{
    private const uint Light = 0xFFFFFFFF;
    private const uint Dark = 0xFF000000;

    private readonly List<BitArray> modules;

    public QrCode(string text)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        modules = data.ModuleMatrix;
    }

    public void Draw(float size)
    {
        var count = modules.Count;
        var cell = size / count;
        var origin = ImGui.GetCursorScreenPos();
        var drawList = ImGui.GetWindowDrawList();
        drawList.AddRectFilled(origin, origin + new Vector2(size), Light);

        for (var y = 0; y < count; y++)
        {
            for (var x = 0; x < count; x++)
            {
                if (!modules[y][x])
                    continue;
                var min = origin + new Vector2(x * cell, y * cell);
                drawList.AddRectFilled(min, min + new Vector2(cell), Dark);
            }
        }

        ImGui.Dummy(new Vector2(size));
    }
}
