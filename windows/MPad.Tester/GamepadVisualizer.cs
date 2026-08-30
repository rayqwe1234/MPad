using System.Globalization;
using System.Windows;
using System.Windows.Media;

namespace MPad.Tester;

internal sealed class GamepadVisualizer : FrameworkElement
{
    private static readonly Brush BodyBrush = new SolidColorBrush(Color.FromRgb(36, 44, 55));
    private static readonly Brush SurfaceBrush = new SolidColorBrush(Color.FromRgb(18, 23, 30));
    private static readonly Brush IdleBrush = new SolidColorBrush(Color.FromRgb(73, 85, 101));
    private static readonly Brush ActiveBrush = new SolidColorBrush(Color.FromRgb(112, 240, 160));
    private static readonly Pen OutlinePen = new(new SolidColorBrush(Color.FromRgb(80, 94, 112)), 2);
    private GamepadSnapshot _state;
    private bool _connected;

    public void Update(GamepadSnapshot state, bool connected)
    {
        _state = state;
        _connected = connected;
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc);
        var scale = Math.Min(ActualWidth / 820d, ActualHeight / 410d);
        var offsetX = (ActualWidth - 820 * scale) / 2;
        var offsetY = (ActualHeight - 410 * scale) / 2;
        dc.PushTransform(new TranslateTransform(offsetX, offsetY));
        dc.PushTransform(new ScaleTransform(scale, scale));

        var body = new StreamGeometry();
        using (var c = body.Open())
        {
            c.BeginFigure(new Point(168, 85), true, true);
            c.BezierTo(new Point(110, 90), new Point(78, 155), new Point(58, 265), true, false);
            c.BezierTo(new Point(43, 350), new Point(102, 387), new Point(158, 324), true, false);
            c.LineTo(new Point(220, 258), true, false);
            c.BezierTo(new Point(320, 301), new Point(500, 301), new Point(600, 258), true, false);
            c.LineTo(new Point(662, 324), true, false);
            c.BezierTo(new Point(718, 387), new Point(777, 350), new Point(762, 265), true, false);
            c.BezierTo(new Point(742, 155), new Point(710, 90), new Point(652, 85), true, false);
            c.BezierTo(new Point(550, 67), new Point(270, 67), new Point(168, 85), true, false);
        }
        body.Freeze();
        dc.DrawGeometry(BodyBrush, OutlinePen, body);

        DrawShoulder(dc, new Rect(160, 54, 150, 36), "LB", GamepadButton.LeftShoulder);
        DrawShoulder(dc, new Rect(510, 54, 150, 36), "RB", GamepadButton.RightShoulder);
        DrawTrigger(dc, new Rect(178, 20, 114, 24), "LT", _state.LeftTrigger);
        DrawTrigger(dc, new Rect(528, 20, 114, 24), "RT", _state.RightTrigger);

        DrawStick(dc, new Point(225, 170), _state.LeftX, _state.LeftY, GamepadButton.LeftThumb, "L");
        DrawStick(dc, new Point(470, 275), _state.RightX, _state.RightY, GamepadButton.RightThumb, "R");
        DrawDpad(dc, new Point(283, 278));

        DrawRoundButton(dc, new Point(638, 158), 27, "Y", GamepadButton.Y, Color.FromRgb(255, 211, 74));
        DrawRoundButton(dc, new Point(690, 210), 27, "B", GamepadButton.B, Color.FromRgb(255, 91, 91));
        DrawRoundButton(dc, new Point(586, 210), 27, "X", GamepadButton.X, Color.FromRgb(89, 168, 255));
        DrawRoundButton(dc, new Point(638, 262), 27, "A", GamepadButton.A, Color.FromRgb(112, 240, 160));
        DrawRoundButton(dc, new Point(365, 171), 19, "⧉", GamepadButton.Back, Color.FromRgb(190, 201, 214));
        DrawRoundButton(dc, new Point(455, 171), 19, "☰", GamepadButton.Start, Color.FromRgb(190, 201, 214));

        if (!_connected)
        {
            dc.DrawRoundedRectangle(new SolidColorBrush(Color.FromArgb(210, 13, 16, 21)), null,
                new Rect(245, 142, 330, 92), 16, 16);
            DrawText(dc, "等待 XInput 手柄…", new Point(410, 177), 22, Brushes.White, TextAlignment.Center);
            DrawText(dc, "请先运行 MPad Companion 并连接手机", new Point(410, 207), 13,
                new SolidColorBrush(Color.FromRgb(157, 170, 187)), TextAlignment.Center);
        }

        dc.Pop();
        dc.Pop();
    }

    private void DrawShoulder(DrawingContext dc, Rect rect, string label, GamepadButton button)
    {
        dc.DrawRoundedRectangle(IsActive(button) ? ActiveBrush : IdleBrush, null, rect, 10, 10);
        DrawText(dc, label, new Point(rect.X + rect.Width / 2, rect.Y + 10), 13,
            IsActive(button) ? Brushes.Black : Brushes.White, TextAlignment.Center);
    }

    private void DrawTrigger(DrawingContext dc, Rect rect, string label, byte value)
    {
        dc.DrawRoundedRectangle(SurfaceBrush, OutlinePen, rect, 8, 8);
        var fillWidth = (rect.Width - 4) * value / 255d;
        if (fillWidth > 0)
        {
            dc.DrawRoundedRectangle(ActiveBrush, null, new Rect(rect.X + 2, rect.Y + 2, fillWidth, rect.Height - 4), 6, 6);
        }
        DrawText(dc, $"{label}  {value,3}", new Point(rect.X + rect.Width / 2, rect.Y + 4), 11,
            value > 120 ? Brushes.Black : Brushes.White, TextAlignment.Center);
    }

    private void DrawStick(DrawingContext dc, Point center, short rawX, short rawY, GamepadButton click, string label)
    {
        const double radius = 50;
        dc.DrawEllipse(SurfaceBrush, OutlinePen, center, radius, radius);
        var x = GamepadSnapshot.Normalize(rawX);
        var y = GamepadSnapshot.Normalize(rawY);
        var knob = new Point(center.X + x * 31, center.Y - y * 31);
        dc.DrawLine(new Pen(ActiveBrush, 3), center, knob);
        dc.DrawEllipse(IsActive(click) ? ActiveBrush : IdleBrush, null, knob, 25, 25);
        DrawText(dc, label, new Point(knob.X, knob.Y - 9), 13,
            IsActive(click) ? Brushes.Black : Brushes.White, TextAlignment.Center);
    }

    private void DrawDpad(DrawingContext dc, Point center)
    {
        DrawDpadPart(dc, new Rect(center.X - 20, center.Y - 57, 40, 48), GamepadButton.DpadUp, "▲");
        DrawDpadPart(dc, new Rect(center.X - 20, center.Y + 9, 40, 48), GamepadButton.DpadDown, "▼");
        DrawDpadPart(dc, new Rect(center.X - 57, center.Y - 20, 48, 40), GamepadButton.DpadLeft, "◀");
        DrawDpadPart(dc, new Rect(center.X + 9, center.Y - 20, 48, 40), GamepadButton.DpadRight, "▶");
        dc.DrawRectangle(IdleBrush, null, new Rect(center.X - 20, center.Y - 20, 40, 40));
    }

    private void DrawDpadPart(DrawingContext dc, Rect rect, GamepadButton button, string label)
    {
        var active = IsActive(button);
        dc.DrawRoundedRectangle(active ? ActiveBrush : IdleBrush, null, rect, 6, 6);
        DrawText(dc, label, new Point(rect.X + rect.Width / 2, rect.Y + rect.Height / 2 - 9), 14,
            active ? Brushes.Black : Brushes.White, TextAlignment.Center);
    }

    private void DrawRoundButton(DrawingContext dc, Point center, double radius, string label, GamepadButton button, Color color)
    {
        var active = IsActive(button);
        var brush = active ? new SolidColorBrush(color) : SurfaceBrush;
        var pen = new Pen(new SolidColorBrush(color), active ? 3 : 2);
        dc.DrawEllipse(brush, pen, center, radius, radius);
        DrawText(dc, label, new Point(center.X, center.Y - 10), 16,
            active ? Brushes.Black : new SolidColorBrush(color), TextAlignment.Center);
    }

    private bool IsActive(GamepadButton button) => _connected && _state.IsPressed(button);

    private static void DrawText(DrawingContext dc, string text, Point point, double size, Brush brush, TextAlignment alignment)
    {
        var formatted = new FormattedText(text, CultureInfo.CurrentUICulture, FlowDirection.LeftToRight,
            new Typeface("Segoe UI"), size, brush, 1.0) { TextAlignment = alignment };
        dc.DrawText(formatted, point);
    }
}
