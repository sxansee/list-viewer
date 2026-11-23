using System.Collections.Generic;
using System.Windows;
using System.Windows.Media;
using Glass.Studio.Services;
using Glass.Studio.ViewModels;
using ICSharpCode.AvalonEdit.Rendering;

namespace Glass.Studio.Editor
{
    /// <summary>
    /// Подсветка целых строк под текстом: строка выполнения (жёлтая/зелёная/красная) и строки
    /// с ошибками компиляции. Рисуется по образцу CurrentLineHighlightRenderer из AvalonEdit.
    /// </summary>
    public sealed class LineHighlightRenderer : IBackgroundRenderer
    {
        private const double StripeWidth = 4;

        // Цвета — из палитры текущей темы (ExecutionFillBrush и другие в Themes/Palette.*.xaml).
        private static Brush CurrentFill => ThemeBrushes.Get("ExecutionFillBrush");
        private static Brush CurrentStripe => ThemeBrushes.Get("ExecutionStripeBrush");
        private static Brush CallerFill => ThemeBrushes.Get("CallerFillBrush");
        private static Brush CallerStripe => ThemeBrushes.Get("CallerStripeBrush");
        private static Brush ErrorFill => ThemeBrushes.Get("ErrorFillBrush");
        private static Brush ErrorStripe => ThemeBrushes.Get("ErrorStripeBrush");
        private static Brush ProblemFill => ThemeBrushes.Get("ProblemFillBrush");

        private readonly HashSet<int> _problemLines = new HashSet<int>();
        private int _executionLine;
        private ExecutionMarker _marker;

        public KnownLayer Layer => KnownLayer.Background;

        public void SetExecution(int? line, ExecutionMarker marker)
        {
            _executionLine = line ?? 0;
            _marker = marker;
        }

        public void SetProblems(IEnumerable<int> lines)
        {
            _problemLines.Clear();
            foreach (int line in lines)
            {
                if (line > 0) _problemLines.Add(line);
            }
        }

        public void Draw(TextView textView, DrawingContext drawingContext)
        {
            foreach (int line in _problemLines)
            {
                if (line != _executionLine) DrawLine(textView, drawingContext, line, ProblemFill, ErrorStripe);
            }

            if (_executionLine <= 0) return;
            switch (_marker)
            {
                case ExecutionMarker.Caller:
                    DrawLine(textView, drawingContext, _executionLine, CallerFill, CallerStripe);
                    break;
                case ExecutionMarker.Error:
                    DrawLine(textView, drawingContext, _executionLine, ErrorFill, ErrorStripe);
                    break;
                default:
                    DrawLine(textView, drawingContext, _executionLine, CurrentFill, CurrentStripe);
                    break;
            }
        }

        private static void DrawLine(TextView textView, DrawingContext drawingContext, int line, Brush fill, Brush stripe)
        {
            VisualLine? visualLine = textView.GetVisualLine(line);
            if (visualLine == null) return;   // строки нет на экране (прокручена или документ короче)

            double top = visualLine.VisualTop - textView.ScrollOffset.Y;
            drawingContext.DrawRectangle(fill, null, new Rect(0, top, textView.ActualWidth, visualLine.Height));
            drawingContext.DrawRectangle(stripe, null, new Rect(0, top, StripeWidth, visualLine.Height));
        }

    }
}
