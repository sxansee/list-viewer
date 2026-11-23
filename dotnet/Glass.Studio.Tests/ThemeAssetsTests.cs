using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Xml.Linq;
using static Glass.Studio.Tests.TestKit;

namespace Glass.Studio.Tests
{
    /// <summary>
    /// Тёмная тема, которую нельзя увидеть без живой Windows, проверяется по тому, что видно в файлах: наборы цветов совпадают,
    /// контраст текста по WCAG достаточен, светлые значения не изменились, в окнах нет «зашитых» цветов, а шаблоны тёмных элементов
    /// не ссылаются на несуществующие части. Цвета и анимацию глазами проверяют на Windows (TODO.md, раздел 2).
    /// </summary>
    public static class ThemeAssetsTests
    {
        private static readonly XNamespace Wpf = "http://schemas.microsoft.com/winfx/2006/xaml/presentation";
        private static readonly XNamespace X = "http://schemas.microsoft.com/winfx/2006/xaml";

        public static void Register()
        {
            Add("тема: палитры светлая и тёмная — одинаковый набор ключей, цвета корректны", PaletteParity);
            Add("тема: светлая палитра сохранила прежние цвета — светлый вид не изменился", LightLookUnchanged);
            Add("тема: тёмная палитра — контраст текста, значков и редактора по WCAG", DarkContrast);
            Add("тема: окна, стили и код WPF не содержат «зашитых» цветов, кисти — только динамические", NoHardCodedColors);
            Add("тема: Controls.Dark.xaml — цвета из палитры, шаблоны согласованы, части на месте", DarkControls);
            Add("тема: подсветка синтаксиса — светлый и тёмный .xshd отличаются только цветами, контраст читаем", HighlightingThemes);
            Add("тема: подключение — App.xaml, ThemeManager, ресурсы, меню «Вид → Тема», запуск", Wiring);
        }

        // ------------------------------------------------------------------ вспомогательное

        private static string Root
        {
            get
            {
                string? dir = AppContext.BaseDirectory;
                while (dir != null && !File.Exists(Path.Combine(dir, "dotnet", "Glass.slnx"))) dir = Path.GetDirectoryName(dir);
                return dir ?? throw new InvalidOperationException("не найден корень репозитория");
            }
        }

        private static string StudioPath(string relative) => Path.Combine(Root, "dotnet", "Glass.Studio", relative);
        private static string Read(string relative) => File.ReadAllText(StudioPath(relative));

        private static Dictionary<string, string> Palette(string file)
        {
            var map = new Dictionary<string, string>();
            var duplicates = new List<string>();
            foreach (XElement e in XDocument.Load(StudioPath("Themes/" + file)).Root!.Elements())
            {
                string? key = (string?)e.Attribute(X + "Key");
                string? color = (string?)e.Attribute("Color");
                if (key == null || color == null) continue;
                if (map.ContainsKey(key)) duplicates.Add(key);
                map[key] = color;
            }
            Rule(file + ": ключи не повторяются", duplicates);
            return map;
        }

        private static double Luminance(string hex)
        {
            string h = hex.TrimStart('#');
            if (h.Length == 8) h = h.Substring(2);   // альфа не учитываем
            double Channel(int at)
            {
                double c = Convert.ToInt32(h.Substring(at, 2), 16) / 255.0;
                return c <= 0.03928 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);
            }
            return 0.2126 * Channel(0) + 0.7152 * Channel(2) + 0.0722 * Channel(4);
        }

        /// <summary>Отношение контрастности WCAG 2.x: 4,5 — читаемый текст, 7 — повышенный уровень, 3 — крупный текст и графика.</summary>
        private static double Contrast(string a, string b)
        {
            double la = Luminance(a), lb = Luminance(b);
            return (Math.Max(la, lb) + 0.05) / (Math.Min(la, lb) + 0.05);
        }

        /// <summary>Одно правило — одна проверка; при провале в сообщении перечислены нарушения (первые восемь).</summary>
        private static void Rule(string rule, IEnumerable<string> violations)
        {
            List<string> list = violations.ToList();
            Check(list.Count == 0, list.Count == 0 ? rule : rule + " — нарушения: " + string.Join("; ", list.Take(8)) + (list.Count > 8 ? "; … и ещё " + (list.Count - 8) : ""));
        }

        private static readonly Regex Hex = new Regex("#[0-9A-Fa-f]{6,8}\\b", RegexOptions.Compiled);

        // ------------------------------------------------------------------ палитры

        private static void PaletteParity()
        {
            Dictionary<string, string> light = Palette("Palette.Light.xaml"), dark = Palette("Palette.Dark.xaml");
            Check(light.Count >= 60, "в палитре не меньше 60 кистей (сейчас " + light.Count + ")");
            Rule("наборы ключей светлой и тёмной палитр совпадают",
                 light.Keys.Except(dark.Keys).Select(k => "нет в тёмной: " + k).Concat(dark.Keys.Except(light.Keys).Select(k => "нет в светлой: " + k)));
            Rule("цвета записаны как #RRGGBB или #AARRGGBB",
                 light.Concat(dark).Where(pair => !Regex.IsMatch(pair.Value, "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")).Select(pair => pair.Key + "=" + pair.Value));
            // Одинаковые в обеих темах бывают лишь осознанно (цвета фишек слотов, акценты, значки); остальное, видимо, забыли подобрать.
            Rule("тёмные значения подобраны (не скопированы из светлой палитры)",
                 dark.Keys.Where(k => dark[k] == light[k] && !k.StartsWith("Slot") && !k.StartsWith("Breakpoint") && !k.StartsWith("State") && !k.StartsWith("Icon")
                                      && !k.StartsWith("Accent") && !k.StartsWith("Ref") && !k.EndsWith("TextBrush") && !k.StartsWith("Editor")));
        }

        private static void LightLookUnchanged()
        {
            // Значения, которые до темы были «зашиты» в Glass.xaml, окнах и коде редактора (сверено с исходным архивом).
            var expected = new Dictionary<string, string>
            {
                ["WindowBackgroundBrush"] = "#F2F4F8", ["PanelBackgroundBrush"] = "#FFFFFF", ["PanelHeaderBrush"] = "#E9EEF5", ["PanelBorderBrush"] = "#D3DAE5",
                ["CanvasBackgroundBrush"] = "#FAFBFD", ["TextBrush"] = "#1F2937", ["MutedTextBrush"] = "#6B7788", ["AccentBrush"] = "#2563EB",
                ["AccentSoftBrush"] = "#E4EDFE", ["RefBrush"] = "#6D3FC4", ["GarbageBrush"] = "#D64545", ["GarbageSoftBrush"] = "#FDECEC",
                ["GarbageHeaderBrush"] = "#F8D5D5", ["NewBrush"] = "#15803D", ["NewSoftBrush"] = "#E3F6EA", ["ChangedBrush"] = "#B45309",
                ["ChangedSoftBrush"] = "#FFF1D6", ["ObjectHeaderBrush"] = "#E3ECFB", ["ArrayHeaderBrush"] = "#DDF3E5", ["NodeBorderBrush"] = "#9AA9BE",
                ["EdgeBrush"] = "#7487A3", ["SlotIntBrush"] = "#3B82F6", ["SlotDoubleBrush"] = "#0D9488", ["SlotBoolBrush"] = "#D97706", ["SlotRefBrush"] = "#8B5CF6",
                ["TitleTextBrush"] = "#3A4656", ["BannerBorderBrush"] = "#F1CF8E", ["StateIdleBrush"] = "#9AA7B8", ["StateRunningBrush"] = "#F59E0B",
                ["StatePausedBrush"] = "#2563EB", ["StateDoneBrush"] = "#16A34A", ["IconPlayBrush"] = "#16A34A", ["IconStopBrush"] = "#DC2626",
                ["IconBreakpointBrush"] = "#E03131", ["IconCleanBrush"] = "#6D3FC4", ["HoverBrush"] = "#EEF2F9", ["SurfaceAltBrush"] = "#F6F8FB",
                ["ChipBackgroundBrush"] = "#E5E9F0", ["CodeBackgroundBrush"] = "#1F2937", ["CodeBorderBrush"] = "#111827", ["CodeTextBrush"] = "#E5E7EB",
                ["EditorBackgroundBrush"] = "#FFFFFF", ["EditorTextBrush"] = "#000000", ["EditorLineNumberBrush"] = "#8A94A6", ["EditorGutterBrush"] = "#F4F6FA",
                ["ExecutionFillBrush"] = "#FFF3B0", ["ExecutionStripeBrush"] = "#F2C200", ["CallerFillBrush"] = "#D9F5DF", ["CallerStripeBrush"] = "#2FA84F",
                ["ErrorFillBrush"] = "#FFD9D6", ["ErrorStripeBrush"] = "#D64545", ["ProblemFillBrush"] = "#FFEAE7", ["BreakpointDotBrush"] = "#E03131",
                ["BreakpointHoverBrush"] = "#55E03131", ["InlineCodeBackgroundBrush"] = "#ECF0F7", ["InlineCodeTextBrush"] = "#9D1B56",
            };
            Dictionary<string, string> light = Palette("Palette.Light.xaml");
            Rule("светлая палитра: значения, существовавшие до темы, не изменились (сверено с исходным архивом)",
                 expected.Where(pair => !light.TryGetValue(pair.Key, out string? value) || !string.Equals(value, pair.Value, StringComparison.OrdinalIgnoreCase))
                         .Select(pair => pair.Key + " должно быть " + pair.Value));
        }

        private static void DarkContrast()
        {
            Dictionary<string, string> d = Palette("Palette.Dark.xaml");
            var pairs = new List<(string Fg, string[] Bg, double Need)>
            {
                ("TextBrush", new[] { "WindowBackgroundBrush", "PanelBackgroundBrush", "PanelHeaderBrush", "CanvasBackgroundBrush", "HoverBrush", "ControlBackgroundBrush",
                                      "MenuBackgroundBrush", "ChipBackgroundBrush", "AccentSoftBrush", "ObjectHeaderBrush", "GarbageSoftBrush", "NewSoftBrush", "ChangedSoftBrush" }, 7),
                ("MutedTextBrush", new[] { "WindowBackgroundBrush", "PanelBackgroundBrush", "PanelHeaderBrush", "CanvasBackgroundBrush", "HoverBrush" }, 4.5),
                ("TitleTextBrush", new[] { "PanelHeaderBrush", "PanelBackgroundBrush" }, 7),
                ("AccentBrush", new[] { "PanelBackgroundBrush", "PanelHeaderBrush", "CanvasBackgroundBrush", "ControlBackgroundBrush" }, 4.5),
                ("RefBrush", new[] { "PanelBackgroundBrush", "CanvasBackgroundBrush" }, 4.5),
                ("GarbageBrush", new[] { "PanelBackgroundBrush", "GarbageSoftBrush", "CanvasBackgroundBrush" }, 4.5),
                ("NewBrush", new[] { "PanelBackgroundBrush", "NewSoftBrush" }, 4.5),
                ("ChangedBrush", new[] { "PanelBackgroundBrush", "ChangedSoftBrush" }, 4.5),
                ("SelectionTextBrush", new[] { "SelectionBrush" }, 7),
                ("CodeTextBrush", new[] { "CodeBackgroundBrush" }, 7),
                ("DisabledTextBrush", new[] { "ControlBackgroundBrush", "PanelBackgroundBrush" }, 3),
                ("IconPlayBrush", new[] { "PanelHeaderBrush", "WindowBackgroundBrush" }, 4.5),
                ("IconStopBrush", new[] { "PanelHeaderBrush", "WindowBackgroundBrush" }, 4.5),
                ("IconBreakpointBrush", new[] { "PanelHeaderBrush", "WindowBackgroundBrush" }, 4.5),
                ("IconCleanBrush", new[] { "PanelHeaderBrush", "WindowBackgroundBrush" }, 4.5),
                ("StateRunningBrush", new[] { "PanelHeaderBrush" }, 3), ("StateDoneBrush", new[] { "PanelHeaderBrush" }, 3), ("StatePausedBrush", new[] { "PanelHeaderBrush" }, 3),
                ("EdgeBrush", new[] { "CanvasBackgroundBrush" }, 3), ("NodeBorderBrush", new[] { "CanvasBackgroundBrush" }, 2),
                ("ScrollThumbBrush", new[] { "ScrollTrackBrush" }, 1.5), ("PanelBorderBrush", new[] { "PanelBackgroundBrush" }, 1.3), ("ControlBorderBrush", new[] { "ControlBackgroundBrush" }, 1.3),
                // редактор кода
                ("EditorTextBrush", new[] { "EditorBackgroundBrush", "ExecutionFillBrush", "CallerFillBrush", "ErrorFillBrush", "ProblemFillBrush" }, 7),
                ("EditorTextBrush", new[] { "EditorSelectionBrush" }, 4.5),
                ("EditorLineNumberBrush", new[] { "EditorGutterBrush", "EditorBackgroundBrush" }, 4.5),
                ("ExecutionStripeBrush", new[] { "EditorBackgroundBrush" }, 3), ("CallerStripeBrush", new[] { "EditorBackgroundBrush" }, 3), ("ErrorStripeBrush", new[] { "EditorBackgroundBrush" }, 3),
                ("BreakpointDotBrush", new[] { "EditorGutterBrush" }, 3), ("InlineCodeTextBrush", new[] { "InlineCodeBackgroundBrush" }, 4.5),
            };
            Rule("тёмная тема: контраст текста, значков и редактора по WCAG не ниже заданного для " + pairs.Sum(pair => pair.Bg.Length) + " пар цветов",
                 pairs.SelectMany(pair => pair.Bg.Select(bg => (pair.Fg, bg, pair.Need)))
                      .Where(x => Contrast(d[x.Fg], d[x.bg]) < x.Need)
                      .Select(x => string.Format("{0} на {1} — {2:0.00} (нужно {3})", x.Fg, x.bg, Contrast(d[x.Fg], d[x.bg]), x.Need)));
        }

        // ------------------------------------------------------------------ «зашитые» цвета

        private static void NoHardCodedColors()
        {
            var files = new List<string> { "MainWindow.xaml", "CourseWindow.xaml", "Themes/Glass.xaml" };
            files.AddRange(Directory.GetFiles(StudioPath("Views"), "*.xaml").Select(f => "Views/" + Path.GetFileName(f)));
            var allowed = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "#E6FFFFFF", "#000000" };   // белый текст на цветных фишках слотов; тень
            var hard = new List<string>();
            var staticBrushes = new List<string>();
            int dynamic = 0;
            foreach (string file in files)
            {
                string text = Regex.Replace(Read(file), "<!--.*?-->", "", RegexOptions.Singleline);
                hard.AddRange(Hex.Matches(text).Select(m => m.Value).Where(v => !allowed.Contains(v)).Select(v => file + ": " + v));
                if (Regex.IsMatch(text, "\\{StaticResource\\s+\\w+Brush\\}")) staticBrushes.Add(file);
                dynamic += Regex.Matches(text, "\\{DynamicResource\\s+\\w+Brush\\}").Count;
            }
            Rule("окна и стили не содержат «зашитых» цветов — они лежат в палитре (Themes/Palette.*.xaml)", hard);
            Rule("кисти подключены через DynamicResource — иначе смена темы их не перекрасит", staticBrushes);
            Check(dynamic >= 100, "окна ссылаются на палитру динамически (ссылок: " + dynamic + ")");

            var code = new List<string>();
            foreach (string path in Directory.GetFiles(StudioPath(""), "*.cs", SearchOption.AllDirectories))
            {
                string rel = Path.GetRelativePath(StudioPath(""), path).Replace('\\', '/');
                if (rel.StartsWith("obj/") || rel.StartsWith("bin/") || rel == "Services/ThemeBrushes.cs") continue;   // там запасная кисть на случай отсутствия ресурса
                if (Regex.IsMatch(File.ReadAllText(path), "Color\\.From|(?<![A-Za-z])Colors\\.|(?<![A-Za-z])Brushes\\.|new SolidColorBrush|0x[0-9A-Fa-f]{6}")) code.Add(rel);
            }
            Rule("код WPF не задаёт цвета сам — только через палитру (ThemeBrushes.Get, SetResourceReference)", code);
        }

        // ------------------------------------------------------------------ стили элементов тёмной темы

        private static void DarkControls()
        {
            XDocument doc = XDocument.Load(StudioPath("Themes/Controls.Dark.xaml"));
            string source = File.ReadAllText(StudioPath("Themes/Controls.Dark.xaml"));
            Dictionary<string, string> dark = Palette("Palette.Dark.xaml");
            var darkValues = new HashSet<string>(dark.Values, StringComparer.OrdinalIgnoreCase);

            // 1. Системные цвета: только значения тёмной палитры; других шестнадцатеричных цветов в файле нет.
            var systemBrushes = doc.Descendants(Wpf + "SolidColorBrush")
                                   .Where(b => ((string?)b.Attribute(X + "Key") ?? "").StartsWith("{x:Static SystemColors.", StringComparison.Ordinal)).ToList();
            Check(systemBrushes.Count >= 20, "переопределены системные цвета (кистей: " + systemBrushes.Count + ")");
            Rule("цвета переопределённых системных кистей есть в Palette.Dark.xaml",
                 systemBrushes.Where(b => !darkValues.Contains((string?)b.Attribute("Color") ?? "")).Select(b => (string?)b.Attribute(X + "Key") + " = " + (string?)b.Attribute("Color")));
            Rule("в атрибутах (кроме Color системных кистей) нет шестнадцатеричных цветов — только ссылки на палитру",
                 doc.Descendants().SelectMany(e => e.Attributes()).Where(a => a.Name.LocalName != "Color" && Hex.IsMatch(a.Value)).Select(a => a.Name.LocalName + "=" + a.Value));

            // 2. Ссылки на кисти разрешаются в тёмной палитре.
            Rule("ссылки {DynamicResource …} указывают на существующие кисти тёмной палитры",
                 Regex.Matches(source, "\\{DynamicResource\\s+(\\w+)\\}").Select(m => m.Groups[1].Value).Where(k => !dark.ContainsKey(k)).Distinct());

            // 3. Шаблоны: TargetName указывает на имена внутри того же шаблона; обязательные части на месте.
            var templates = doc.Descendants(Wpf + "ControlTemplate").ToList();
            Check(templates.Count >= 12, "шаблонов элементов не меньше 12 (сейчас " + templates.Count + ")");
            var problems = new List<string>();
            foreach (XElement template in templates)
            {
                string target = (string?)template.Attribute("TargetType") ?? "";
                var names = new HashSet<string>(template.DescendantsAndSelf().Select(e => (string?)e.Attribute(X + "Name")).OfType<string>());
                foreach (XAttribute a in template.Descendants().Select(e => e.Attribute("TargetName")).OfType<XAttribute>())
                    if (!names.Contains(a.Value)) problems.Add("шаблон " + target + ": TargetName «" + a.Value + "» не объявлен внутри");
                switch (target)
                {
                    case "ScrollBar":
                        if (!names.Contains("PART_Track") || !template.Descendants(Wpf + "Track").Any()) problems.Add("ScrollBar: нет Track с именем PART_Track");
                        break;
                    case "ComboBox":
                        if (!names.Contains("PART_Popup") || !template.Descendants(Wpf + "Popup").Any()) problems.Add("ComboBox: нет Popup с именем PART_Popup");
                        break;
                    case "GridViewColumnHeader":
                        if (!names.Contains("PART_HeaderGripper")) problems.Add("GridViewColumnHeader: нет PART_HeaderGripper");
                        break;
                    case "ProgressBar":
                        if (!names.Contains("PART_Track") || !names.Contains("PART_Indicator")) problems.Add("ProgressBar: нет PART_Track или PART_Indicator");
                        break;
                    case "TabItem":
                        if (!template.Descendants(Wpf + "ContentPresenter").Any(p => (string?)p.Attribute("ContentSource") == "Header")) problems.Add("TabItem: заголовок не выводится (ContentSource=Header)");
                        break;
                }
            }
            Rule("шаблоны согласованы: TargetName указывает на имена внутри шаблона, обязательные части (PART_*) на месте", problems);

            // 4. Покрыто всё, что использует приложение.
            var styled = new HashSet<string>(doc.Descendants(Wpf + "Style").Where(s => (string?)s.Attribute(X + "Key") == null).Select(s => (string?)s.Attribute("TargetType") ?? ""));
            var keyed = doc.Descendants(Wpf + "Style").Select(s => (string?)s.Attribute(X + "Key") ?? "").ToList();
            var missing = new[] { "Button", "ToggleButton", "TabItem", "ComboBox", "ComboBoxItem", "GridViewColumnHeader", "ProgressBar", "ScrollBar", "ToolTip", "TextBox", "GridSplitter", "ToolBar", "Menu" }
                          .Where(type => !styled.Contains(type)).Select(type => "стиль " + type).ToList();
            if (!keyed.Any(k => k.Contains("ToolBar.ButtonStyleKey"))) missing.Add("кнопки панели инструментов (ToolBar.ButtonStyleKey)");
            if (!keyed.Any(k => k.Contains("MenuItem.SeparatorStyleKey"))) missing.Add("разделитель меню (MenuItem.SeparatorStyleKey)");
            Rule("в тёмной теме есть стили для всех элементов, которые использует приложение", missing);
        }

        // ------------------------------------------------------------------ подсветка синтаксиса

        private static void HighlightingThemes()
        {
            XDocument light = XDocument.Load(StudioPath("Editor/Glass.xshd")), dark = XDocument.Load(StudioPath("Editor/Glass.Dark.xshd"));
            XDocument Normalize(XDocument d)
            {
                var copy = new XDocument(d);
                copy.Root!.Attribute("name")?.Remove();
                foreach (XElement color in copy.Descendants().Where(e => e.Name.LocalName == "Color")) color.Attribute("foreground")?.Remove();
                return copy;
            }
            Check(XNode.DeepEquals(Normalize(light), Normalize(dark)), "правила подсветки в светлом и тёмном .xshd одинаковы — отличаются только цвета (иначе слова разъедутся)");

            Dictionary<string, string> palette = Palette("Palette.Dark.xaml"), lightPalette = Palette("Palette.Light.xaml");
            foreach (var (doc, background, need, label) in new[] { (light, lightPalette["EditorBackgroundBrush"], 4.3, "светлая"), (dark, palette["EditorBackgroundBrush"], 4.5, "тёмная") })
            {
                Rule(label + " подсветка: контраст цветов на фоне редактора не ниже " + need,
                     doc.Descendants().Where(e => e.Name.LocalName == "Color")
                        .Where(c => Contrast((string?)c.Attribute("foreground") ?? "#000000", background) < need)
                        .Select(c => (string?)c.Attribute("name") + " " + (string?)c.Attribute("foreground")));
                Check(doc.Descendants().Any(e => e.Name.LocalName == "Span" && (string?)e.Attribute("multiline") == "true" && e.Elements().Any(b => b.Name.LocalName == "Begin" && b.Value == "/\\*")),
                      label + " подсветка: блочные комментарии /* … */ выделены");
            }
        }

        // ------------------------------------------------------------------ подключение

        private static void Wiring()
        {
            Check(Read("App.xaml").Contains("Themes/Glass.xaml") && Read("App.xaml").Contains("Themes/Palette.Light.xaml"), "App.xaml подключает стили и светлую палитру по умолчанию");
            string manager = Read("Services/ThemeManager.cs");
            foreach (string file in new[] { "Themes/Palette.Light.xaml", "Themes/Palette.Dark.xaml", "Themes/Controls.Dark.xaml" })
            {
                Check(File.Exists(StudioPath(file)), file + ": файл на месте");
                Check(manager.Contains("\"" + file + "\""), "ThemeManager ссылается на " + file);
            }
            string project = Read("Glass.Studio.csproj");
            foreach (string xshd in new[] { "Glass.xshd", "Glass.Dark.xshd" })
                Check(project.Contains("<EmbeddedResource Include=\"Editor\\" + xshd + "\" LogicalName=\"Glass.Studio.Editor." + xshd + "\" />"), "csproj встраивает " + xshd);
            string highlighting = Read("Editor/GlassHighlighting.cs");
            Check(highlighting.Contains("Glass.Studio.Editor.Glass.xshd") && highlighting.Contains("Glass.Studio.Editor.Glass.Dark.xshd"), "GlassHighlighting знает оба ресурса");
            string window = Read("MainWindow.xaml");
            foreach (string command in new[] { "SetThemeLightCommand", "SetThemeDarkCommand", "SetThemeSystemCommand" })
                Check(window.Contains(command), "меню «Вид → Тема»: команда " + command);
            string code = Read("MainWindow.xaml.cs");
            Check(code.Contains("themeHost: _themeManager") && code.Contains("_themeManager.Register(this)") && code.Contains("_viewModel.ApplyTheme()"),
                  "MainWindow: ThemeManager передан в ViewModel, окно зарегистрировано, тема применяется до показа");
            Check(code.Contains("_themeManager.Register(_courseWindow)"), "окно курса тоже следует теме");
            Check(Read("Editor/EditorHost.cs").Contains("public void ApplyTheme(bool dark)"), "EditorHost умеет менять тему");
        }
    }
}
