using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Glass.Studio.ViewModels;
using static Glass.Studio.Tests.TestKit;

namespace Glass.Studio.Tests
{
    /// <summary>Выбор темы («Вид → Тема»): запоминание, применение к окнам, вариант «как в системе».</summary>
    public static class ThemeTests
    {
        public static void Register()
        {
            Add("тема: по умолчанию светлая, применяется только по команде окна", DefaultIsLight);
            Add("тема: выбор тёмной — применяется сразу, один раз, уведомляет меню", ChoosingDark);
            Add("тема: «как в системе» следует за Windows и реагирует на её смену", FollowsSystem);
            Add("тема: выбор запоминается между запусками; в файле — словом", Persistence);
        }

        private sealed class ThemeHost : IThemeHost
        {
            public bool SystemPrefersDark { get; set; }
            public List<bool> Applied { get; } = new List<bool>();
            public void ApplyTheme(bool dark) => Applied.Add(dark);
        }

        private sealed class Store : ISettingsStore
        {
            public UserSettings Stored = new UserSettings();
            public UserSettings Load() => Stored;

            public bool Save(UserSettings settings)
            {
                Stored = new UserSettings { RecentFiles = settings.RecentFiles.ToList(), EditorFontSize = settings.EditorFontSize, Theme = settings.Theme };
                return true;
            }
        }

        private static MainViewModel Make(Store store, ThemeHost host) =>
            new MainViewModel(new FakeEditor(), new FakeDialogs(), liveCheckDelayMs: -1, settings: store, themeHost: host);

        private static void DefaultIsLight()
        {
            var host = new ThemeHost { SystemPrefersDark = true };
            MainViewModel vm = Make(new Store(), host);
            CheckEqual(ThemeChoice.Light, vm.Theme, "по умолчанию — светлая (тот вид, что проверен глазами)");
            Check(vm.IsThemeLight && !vm.IsThemeDark && !vm.IsThemeSystem, "отмечен только пункт «Светлая»");
            Check(!vm.IsDarkThemeActive, "светлая действует, даже если Windows тёмная");
            CheckEqual(0, host.Applied.Count, "сама по себе ViewModel окна не перекрашивает — это делает окно при запуске");
            vm.ApplyTheme();
            CheckEqual("False", string.Join(",", host.Applied), "ApplyTheme() применяет светлую");
        }

        private static void ChoosingDark()
        {
            var host = new ThemeHost();
            MainViewModel vm = Make(new Store(), host);
            var changed = new List<string>();
            vm.PropertyChanged += (s, e) => changed.Add(e.PropertyName ?? "");

            vm.SetThemeDarkCommand.Execute(null);
            CheckEqual(ThemeChoice.Dark, vm.Theme, "тема — тёмная");
            Check(vm.IsThemeDark && !vm.IsThemeLight && !vm.IsThemeSystem && vm.IsDarkThemeActive, "отмечен пункт «Тёмная», тема действует");
            CheckEqual("True", string.Join(",", host.Applied), "окнам передана тёмная тема — один раз");
            foreach (string name in new[] { "Theme", "IsThemeDark", "IsThemeLight", "IsThemeSystem" })
                Check(changed.Contains(name), "меню уведомлено: " + name);

            vm.SetThemeDarkCommand.Execute(null);
            vm.Theme = ThemeChoice.Dark;
            CheckEqual(1, host.Applied.Count, "повторный выбор той же темы ничего не делает");
            vm.Theme = (ThemeChoice)42;
            CheckEqual(ThemeChoice.Dark, vm.Theme, "несуществующее значение игнорируется");

            vm.SetThemeLightCommand.Execute(null);
            CheckEqual("True,False", string.Join(",", host.Applied.Select(x => x.ToString())), "обратно на светлую");
        }

        private static void FollowsSystem()
        {
            var host = new ThemeHost { SystemPrefersDark = true };
            MainViewModel vm = Make(new Store(), host);
            vm.SetThemeSystemCommand.Execute(null);
            Check(vm.IsThemeSystem && vm.IsDarkThemeActive, "Windows тёмная — действует тёмная");
            CheckEqual("True", string.Join(",", host.Applied.Select(x => x.ToString())), "применена тёмная");

            host.SystemPrefersDark = false;
            vm.RefreshSystemTheme();
            Check(!vm.IsDarkThemeActive, "Windows стала светлой — «как в системе» тоже");
            CheckEqual("True,False", string.Join(",", host.Applied.Select(x => x.ToString())), "окнам передана светлая");

            vm.SetThemeDarkCommand.Execute(null);
            int before = host.Applied.Count;
            host.SystemPrefersDark = false;
            vm.RefreshSystemTheme();
            CheckEqual(before, host.Applied.Count, "при явном выборе смена темы Windows ничего не меняет");
        }

        private static void Persistence()
        {
            var store = new Store();
            Make(store, new ThemeHost()).SetThemeDarkCommand.Execute(null);
            CheckEqual(ThemeChoice.Dark, store.Stored.Theme, "выбор записан в настройки");

            var host = new ThemeHost();
            MainViewModel next = Make(store, host);
            CheckEqual(ThemeChoice.Dark, next.Theme, "новый запуск читает тёмную тему");
            next.ApplyTheme();
            CheckEqual("True", string.Join(",", host.Applied.Select(x => x.ToString())), "и применяет её при старте");

            // Настоящий файл: тема записана словом, чужие значения не ломают запуск.
            string dir = Path.Combine(Path.GetTempPath(), "glass-theme-" + Guid.NewGuid().ToString("N"));
            try
            {
                string path = Path.Combine(dir, "settings.json");
                var real = new UserSettingsStore(path);
                Check(real.Save(new UserSettings { Theme = ThemeChoice.Dark }), "файл записан");
                Check(File.ReadAllText(path).Contains("\"Theme\": \"Dark\""), "в файле: \"Theme\": \"Dark\"");
                CheckEqual(ThemeChoice.Dark, real.Load().Theme, "прочитано обратно");
                File.WriteAllText(path, "{ \"Theme\": 17 }");
                CheckEqual(ThemeChoice.Light, real.Load().Theme, "число вне набора — светлая тема");
                File.WriteAllText(path, "{ \"Theme\": \"Neon\" }");
                CheckEqual(ThemeChoice.Light, real.Load().Theme, "неизвестное слово — светлая тема, запуск не падает");
            }
            finally
            {
                try { Directory.Delete(dir, true); } catch (IOException) { }
            }
        }
    }
}
