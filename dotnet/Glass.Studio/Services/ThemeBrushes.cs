using System.Windows;
using System.Windows.Media;

namespace Glass.Studio.Services
{
    /// <summary>
    /// Кисти действующей темы из ресурсов приложения — для кода, который рисует сам (подсветка строк, поле точек останова).
    /// Цвета живут только в палитре (Themes/Palette.*.xaml): так их видит тест контраста, и смена темы перекрашивает и этот код.
    /// </summary>
    internal static class ThemeBrushes
    {
        public static Brush Get(string key) => Application.Current?.TryFindResource(key) as Brush ?? Brushes.Gray;
    }
}
