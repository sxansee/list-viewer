using Glass.Studio.ViewModels;

namespace Glass.Studio
{
    /// <summary>Тема окон, которую выбрал пользователь («Вид → Тема»).</summary>
    public enum ThemeChoice
    {
        /// <summary>Светлая — вид по умолчанию.</summary>
        Light = 0,

        /// <summary>Тёмная: палитра, элементы управления, редактор кода и схема кучи.</summary>
        Dark = 1,

        /// <summary>Как в Windows («Параметры → Персонализация → Цвета → Режим приложений по умолчанию»).</summary>
        System = 2,
    }
}

namespace Glass.Studio.ViewModels
{
    /// <summary>
    /// Что нужно от окна приложения, чтобы сменить тему. Настоящая реализация (подмена словарей ресурсов WPF, тема редактора,
    /// заголовки окон) лежит в проекте Glass.Studio, а ViewModel и тесты обходятся без WPF.
    /// </summary>
    public interface IThemeHost
    {
        /// <summary>Включён ли в Windows тёмный режим приложений — нужно для варианта «как в системе».</summary>
        bool SystemPrefersDark { get; }

        /// <summary>Применить тему ко всем окнам: true — тёмную, false — светлую.</summary>
        void ApplyTheme(bool dark);
    }
}
