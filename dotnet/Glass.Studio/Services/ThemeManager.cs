using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Markup;
using Glass.Studio.Editor;
using Glass.Studio.Logging;
using Glass.Studio.ViewModels;
using Microsoft.Win32;

namespace Glass.Studio.Services
{
    /// <summary>
    /// Перекрашивает приложение (IThemeHost для ViewModel): подменяет палитру в ресурсах приложения, подключает или убирает
    /// стили стандартных элементов тёмной темы, переключает тему редактора и заголовки окон.
    /// Если тёмная тема не применилась (опечатка в XAML, ошибка ресурсов), возвращается светлая — окно остаётся рабочим.
    /// </summary>
    internal sealed class ThemeManager : IThemeHost, IDisposable
    {
        private const string LightPalette = "Themes/Palette.Light.xaml";
        private const string DarkPalette = "Themes/Palette.Dark.xaml";
        private const string DarkControls = "Themes/Controls.Dark.xaml";

        private const int DwmUseImmersiveDarkMode = 20;              // Windows 10 20H1 (сборка 19041) и новее, Windows 11
        private const int DwmUseImmersiveDarkModeBefore20H1 = 19;    // ранние сборки Windows 10

        private readonly EditorHost _editor;
        private readonly ILog _log;
        private readonly List<WeakReference<Window>> _windows = new List<WeakReference<Window>>();
        private bool _dark;

        public ThemeManager(EditorHost editor, ILog log)
        {
            _editor = editor ?? throw new ArgumentNullException(nameof(editor));
            _log = log ?? throw new ArgumentNullException(nameof(log));
            SystemEvents.UserPreferenceChanged += OnUserPreferenceChanged;
        }

        /// <summary>Windows сменила режим приложений (светлый/тёмный) — нужно только варианту «как в системе».</summary>
        public event Action? SystemThemeChanged;

        public bool SystemPrefersDark
        {
            get
            {
                try
                {
                    using RegistryKey? key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
                    return key?.GetValue("AppsUseLightTheme") is int light && light == 0;
                }
                catch (Exception ex) when (ex is System.Security.SecurityException || ex is IOException || ex is UnauthorizedAccessException)
                {
                    return false;   // не смогли прочитать настройку — считаем светлой, как по умолчанию
                }
            }
        }

        public void ApplyTheme(bool dark)
        {
            try
            {
                SwapDictionaries(dark);
            }
            catch (Exception ex) when (ex is IOException || ex is XamlParseException || ex is InvalidOperationException)
            {
                _log.Error("Тема", "Не удалось применить " + (dark ? "тёмную" : "светлую") + " тему", ex);
                if (dark)
                {
                    try { SwapDictionaries(false); } catch (Exception inner) when (inner is IOException || inner is XamlParseException || inner is InvalidOperationException) { }
                }
                dark = false;
            }

            _dark = dark;
            _editor.ApplyTheme(dark);
            foreach (Window window in LiveWindows()) ApplyTitleBar(window, dark);
        }

        /// <summary>Окно, чей заголовок должен следовать теме (главное, курс). Заголовок красит Windows, а не WPF, — отдельный вызов.</summary>
        public void Register(Window window)
        {
            _windows.Add(new WeakReference<Window>(window));
            window.SourceInitialized += (_, _) => ApplyTitleBar(window, _dark);
            if (new WindowInteropHelper(window).Handle != IntPtr.Zero) ApplyTitleBar(window, _dark);   // окно уже показано
        }

        public void Dispose() => SystemEvents.UserPreferenceChanged -= OnUserPreferenceChanged;

        private static void SwapDictionaries(bool dark)
        {
            var merged = Application.Current.Resources.MergedDictionaries;

            // Палитра: сначала добавляем новую, потом убираем старую. При поиске ресурса выигрывает последний добавленный словарь,
            // поэтому ни на миг не остаётся окон «без ключей».
            ResourceDictionary? oldPalette = merged.FirstOrDefault(d => d.Source?.OriginalString.Contains("Palette.", StringComparison.OrdinalIgnoreCase) == true);
            merged.Add(new ResourceDictionary { Source = Pack(dark ? DarkPalette : LightPalette) });
            if (oldPalette != null) merged.Remove(oldPalette);

            // Стили стандартных элементов — только в тёмной теме; светлый вид (проверенный на живой Windows) от них не зависит.
            ResourceDictionary? controls = merged.FirstOrDefault(d => d.Source?.OriginalString.EndsWith("Controls.Dark.xaml", StringComparison.OrdinalIgnoreCase) == true);
            if (dark && controls == null) merged.Add(new ResourceDictionary { Source = Pack(DarkControls) });
            else if (!dark && controls != null) merged.Remove(controls);
        }

        private static Uri Pack(string relative) => new Uri("pack://application:,,,/" + relative, UriKind.Absolute);

        private IEnumerable<Window> LiveWindows()
        {
            _windows.RemoveAll(reference => !reference.TryGetTarget(out _));
            foreach (WeakReference<Window> reference in _windows.ToList())
            {
                if (reference.TryGetTarget(out Window? window)) yield return window;
            }
        }

        private void OnUserPreferenceChanged(object? sender, UserPreferenceChangedEventArgs e)
        {
            if (e.Category != UserPreferenceCategory.General) return;
            // Событие приходит из служебного потока Windows, а обновлять окна нужно в потоке интерфейса.
            Application.Current?.Dispatcher.BeginInvoke(new Action(() => SystemThemeChanged?.Invoke()));
        }

        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

        private static void ApplyTitleBar(Window window, bool dark)
        {
            IntPtr handle = new WindowInteropHelper(window).Handle;
            if (handle == IntPtr.Zero) return;   // окно ещё не создано: заголовок подхватится в SourceInitialized
            try
            {
                int value = dark ? 1 : 0;
                if (DwmSetWindowAttribute(handle, DwmUseImmersiveDarkMode, ref value, sizeof(int)) != 0)
                    DwmSetWindowAttribute(handle, DwmUseImmersiveDarkModeBefore20H1, ref value, sizeof(int));
            }
            catch (Exception ex) when (ex is DllNotFoundException || ex is EntryPointNotFoundException)
            {
                // Нет dwmapi или атрибута (очень старая Windows): остаётся обычный светлый заголовок, остальное окно тёмное.
            }
        }
    }
}
