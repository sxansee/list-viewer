using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using static Glass.Studio.Tests.TestKit;

namespace Glass.Studio.Tests
{
    /// <summary>
    /// Гигиена репозитория: версия, адреса и служебные файлы согласованы между собой. Такие вещи ломаются
    /// тихо (забыли поднять номер в одном месте, остался чужой адрес, скрипт перестал запускать набор тестов),
    /// а замечаются уже при релизе.
    /// </summary>
    public static class RepoTests
    {
        public static void Register()
        {
            Add("репозиторий: версия едина (Directory.Build.props, CMakeLists.txt, AppInfo, CHANGELOG)", VersionsAgree);
            Add("репозиторий: нет адресов-заглушек, ссылки ведут на свой репозиторий", RepositoryUrlConsistent);
            Add("репозиторий: служебные файлы на месте (лицензия, CI, релиз, шаблоны)", HousekeepingFiles);
            Add("репозиторий: workflow ссылаются на существующие скрипты и проекты", WorkflowReferences);
            Add("репозиторий: test-all.* запускают все C++-наборы из tests/CMakeLists.txt", ScriptsCoverAllSuites);
            Add("репозиторий: тесты, линкуемые с glass_capi, получают DLL рядом с exe (на Windows нет RPATH)", DllNextToTests);
            Add("репозиторий: решение dotnet/Glass.slnx перечисляет все проекты; старого .sln и ссылок на него нет", SolutionFile);
            Add("репозиторий: значок — корректный .ico, SVG-исходники, подключён к exe и окнам", IconAssets);
        }

        private static string? FindRoot()
        {
            for (DirectoryInfo? dir = new DirectoryInfo(AppContext.BaseDirectory); dir != null; dir = dir.Parent)
            {
                if (File.Exists(Path.Combine(dir.FullName, "CMakeLists.txt")) && Directory.Exists(Path.Combine(dir.FullName, "dotnet")))
                    return dir.FullName;
            }
            return null;
        }

        private static string Full(string relative)
        {
            string root = FindRoot() ?? throw new InvalidOperationException("не найден корень репозитория");
            return Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar));
        }

        private static bool Exists(string relative) => File.Exists(Full(relative)) || Directory.Exists(Full(relative));

        /// <summary>Текст файла репозитория; нет файла — пустая строка (проверки сами скажут, чего не хватает).</summary>
        private static string Read(string relative) => File.Exists(Full(relative)) ? File.ReadAllText(Full(relative)) : "";

        // ------------------------------------------------------------------ версия

        private static void VersionsAgree()
        {
            Match props = Regex.Match(Read("dotnet/Directory.Build.props"), @"<Version>\s*([^<\s]+)\s*</Version>");
            Check(props.Success, "Directory.Build.props: есть <Version>");
            if (!props.Success) return;
            string version = props.Groups[1].Value;
            Check(Regex.IsMatch(version, @"^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$"), "версия — семантическая (X.Y.Z[-суффикс]): " + version);

            Match cmake = Regex.Match(Read("CMakeLists.txt"), @"project\(\s*Glass\s+VERSION\s+(\d+\.\d+\.\d+)");
            Check(cmake.Success, "CMakeLists.txt: project(Glass VERSION X.Y.Z ...)");
            if (cmake.Success)
                CheckEqual(version.Split('-')[0], cmake.Groups[1].Value, "версия в CMakeLists.txt (числовая часть) совпадает с Directory.Build.props");

            CheckEqual(version, AppInfo.Version, "AppInfo.Version (из сборки) совпадает с Directory.Build.props");
            Check(Read("CHANGELOG.md").Contains("## [" + version + "]"), "CHANGELOG.md: есть раздел «## [" + version + "]»");
        }

        // ------------------------------------------------------------------ адреса

        private static readonly string[] TextExtensions = { ".md", ".yml", ".yaml", ".cs", ".props", ".csproj", ".xaml", ".ps1", ".sh", ".txt", ".cpp", ".h" };
        private static readonly string[] SkippedDirs = { "build", "bin", "obj", "out", ".git", ".vs" };

        private static IEnumerable<string> TextFiles(string root)
        {
            foreach (string file in Directory.EnumerateFiles(root))
                if (TextExtensions.Contains(Path.GetExtension(file))) yield return file;
            foreach (string dir in Directory.EnumerateDirectories(root))
            {
                if (SkippedDirs.Contains(Path.GetFileName(dir))) continue;
                foreach (string file in TextFiles(dir)) yield return file;
            }
        }

        private static void RepositoryUrlConsistent()
        {
            string placeholder = "your" + "-name";   // склеено, чтобы сам тест не содержал заглушку
            Match url = Regex.Match(AppInfo.RepositoryUrl, @"^https://github\.com/([^/\s]+/[^/\s]+)$");
            Check(url.Success, "AppInfo.RepositoryUrl — адрес вида https://github.com/<владелец>/<репозиторий>: " + AppInfo.RepositoryUrl);
            Check(!AppInfo.RepositoryUrl.Contains(placeholder), "AppInfo.RepositoryUrl — не заглушка");

            Check(Read("README.md").Contains(AppInfo.RepositoryUrl), "README.md (бейджи, ссылки) ведёт на " + AppInfo.RepositoryUrl);
            Check(Read("dotnet/Directory.Build.props").Contains(AppInfo.RepositoryUrl), "Directory.Build.props: RepositoryUrl совпадает с AppInfo");

            string root = FindRoot()!;
            var withPlaceholder = TextFiles(root).Where(f => File.ReadAllText(f).Contains(placeholder)).Select(f => Path.GetRelativePath(root, f)).ToList();
            CheckEqual("", string.Join(", ", withPlaceholder), "нигде в репозитории не осталось адреса-заглушки");
        }

        // ------------------------------------------------------------------ служебные файлы

        private static void HousekeepingFiles()
        {
            string[] required =
            {
                "LICENSE", ".gitattributes", "CONTRIBUTING.md", "CHANGELOG.md", "RELEASING.md", "THIRD-PARTY-NOTICES.md",
                ".github/workflows/ci.yml", ".github/workflows/release.yml", ".github/PULL_REQUEST_TEMPLATE.md",
                ".github/ISSUE_TEMPLATE/bug_report.md", ".github/ISSUE_TEMPLATE/feature_request.md", "dotnet/Directory.Build.props",
            };
            foreach (string file in required) Check(Exists(file), "есть " + file);
            Check(Read("LICENSE").Contains("MIT License"), "LICENSE — MIT");
        }

        private static void WorkflowReferences()
        {
            foreach (string workflow in new[] { ".github/workflows/ci.yml", ".github/workflows/release.yml" })
            {
                string text = Read(workflow);
                Check(text.Length > 0, workflow + ": файл не пуст");
                var paths = Regex.Matches(text, @"(?:\./)?((?:scripts|dotnet)/[\w./-]+\.(?:sh|ps1|slnx?|csproj|props))")
                                 .Select(m => m.Groups[1].Value).Distinct().ToList();
                Check(paths.Count > 0, workflow + ": найдены ссылки на файлы репозитория");
                foreach (string path in paths) Check(Exists(path), workflow + " ссылается на существующий " + path);
            }
        }

        // ------------------------------------------------------------------ решение .slnx

        // ------------------------------------------------------------------ значок

        private static void IconAssets()
        {
            foreach (string file in new[] { "assets/glass.ico", "assets/glass-icon.svg", "assets/glass-icon-small.svg", "assets/glass-icon-512.png" })
                Check(Exists(file), file + ": файл на месте");

            // ICO разбираем сами: заголовок, таблица кадров, границы данных, сигнатура PNG или заголовок DIB.
            // Битый значок ломает сборку exe (ApplicationIcon) уже на Windows — здесь это видно на Linux.
            if (File.Exists(Full("assets/glass.ico")))
            {
                byte[] ico = File.ReadAllBytes(Full("assets/glass.ico"));
                CheckEqual(0, (int)BitConverter.ToUInt16(ico, 0), "ICO: зарезервированное поле равно 0");
                CheckEqual(1, (int)BitConverter.ToUInt16(ico, 2), "ICO: тип 1 — значок");
                int count = BitConverter.ToUInt16(ico, 4);
                var sizes = new List<int>();
                for (int i = 0; i < count; i++)
                {
                    int entry = 6 + 16 * i;
                    int size = ico[entry] == 0 ? 256 : ico[entry];
                    int height = ico[entry + 1] == 0 ? 256 : ico[entry + 1];
                    sizes.Add(size);
                    CheckEqual(size, height, "ICO " + size + ": кадр квадратный");
                    int length = (int)BitConverter.ToUInt32(ico, entry + 8);
                    int offset = (int)BitConverter.ToUInt32(ico, entry + 12);
                    Check(offset >= 6 + 16 * count && offset + length <= ico.Length, "ICO " + size + ": данные кадра внутри файла");
                    if (offset + 12 > ico.Length) continue;
                    bool png = ico[offset] == 0x89 && ico[offset + 1] == 0x50 && ico[offset + 2] == 0x4E && ico[offset + 3] == 0x47;
                    if (!png)
                    {
                        CheckEqual(40, (int)BitConverter.ToUInt32(ico, offset), "ICO " + size + ": заголовок DIB — 40 байт");
                        CheckEqual(size, BitConverter.ToInt32(ico, offset + 4), "ICO " + size + ": ширина в заголовке DIB");
                        CheckEqual(size * 2, BitConverter.ToInt32(ico, offset + 8), "ICO " + size + ": высота в заголовке DIB удвоена (маска AND)");
                    }
                }
                foreach (int needed in new[] { 16, 24, 32, 48, 256 })
                    Check(sizes.Contains(needed), "ICO: есть кадр " + needed + "×" + needed);
            }

            foreach (string svg in new[] { "assets/glass-icon.svg", "assets/glass-icon-small.svg" })
            {
                try
                {
                    var doc = System.Xml.Linq.XDocument.Parse(Read(svg));
                    CheckEqual("svg", doc.Root?.Name.LocalName ?? "", svg + ": корневой элемент svg");
                    CheckEqual("0 0 256 256", (string?)doc.Root?.Attribute("viewBox") ?? "", svg + ": viewBox 0 0 256 256");
                }
                catch (System.Xml.XmlException ex)
                {
                    Check(false, svg + ": разбирается как XML (" + ex.Message + ")");
                }
            }

            string project = Read("dotnet/Glass.Studio/Glass.Studio.csproj");
            Check(project.Contains(@"<ApplicationIcon>..\..\assets\glass.ico</ApplicationIcon>"), "csproj: ApplicationIcon указывает на assets/glass.ico");
            Check(project.Contains(@"<Resource Include=""..\..\assets\glass.ico"" Link=""Assets\glass.ico"""), "csproj: значок включён в ресурсы приложения");
            foreach (string window in new[] { "dotnet/Glass.Studio/MainWindow.xaml", "dotnet/Glass.Studio/CourseWindow.xaml" })
                Check(Read(window).Contains(@"Icon=""pack://application:,,,/Assets/glass.ico"""), window + ": у окна задан значок");
        }

        private static void SolutionFile()
        {
            string oldName = "Glass" + ".sln";   // склеено, чтобы сам тест не считался «ссылкой на старое решение»
            Check(Exists("dotnet/Glass.slnx"), "есть dotnet/Glass.slnx");
            Check(!Exists("dotnet/" + oldName), "старого dotnet/" + oldName + " нет (два решения — путаница)");

            string root = FindRoot()!;
            string dotnetDir = Path.Combine(root, "dotnet");
            var inSolution = Regex.Matches(Read("dotnet/Glass.slnx"), @"<Project\s+Path=""([^""]+)""")
                                  .Select(m => m.Groups[1].Value.Replace('\\', '/')).OrderBy(p => p, StringComparer.Ordinal).ToList();
            var onDisk = Directory.EnumerateFiles(dotnetDir, "*.csproj", SearchOption.AllDirectories)
                                  .Where(f => !f.Contains(Path.DirectorySeparatorChar + "obj" + Path.DirectorySeparatorChar) &&
                                              !f.Contains(Path.DirectorySeparatorChar + "bin" + Path.DirectorySeparatorChar))
                                  .Select(f => Path.GetRelativePath(dotnetDir, f).Replace('\\', '/')).OrderBy(p => p, StringComparer.Ordinal).ToList();
            CheckEqual(string.Join(", ", onDisk), string.Join(", ", inSolution), "проекты в Glass.slnx = проекты на диске");

            var stale = TextFiles(root).Where(f => Regex.IsMatch(File.ReadAllText(f), @"Glass\.sln\b(?!x)"))
                                       .Select(f => Path.GetRelativePath(root, f)).ToList();
            CheckEqual("", string.Join(", ", stale), "документы и CI ссылаются на Glass.slnx, а не на старое решение");
        }

        // ------------------------------------------------------------------ DLL рядом с тестами

        private static void DllNextToTests()
        {
            string cmake = Read("tests/CMakeLists.txt");
            var targets = Regex.Matches(cmake, @"target_link_libraries\(\s*(\w+)\s+PRIVATE[^)]*\bglass_capi\b")
                               .Select(m => m.Groups[1].Value).Distinct().ToList();
            Check(targets.Count > 0, "tests/CMakeLists.txt: найден хотя бы один тест, использующий glass_capi");
            foreach (string target in targets)
            {
                bool copied = Regex.IsMatch(cmake, @"add_custom_command\(\s*TARGET\s+" + Regex.Escape(target) + @"\s+POST_BUILD[^)]*glass_capi");
                Check(copied, target + ": после сборки glass_capi.dll копируется рядом с exe (иначе на Windows тест не запускается: код 0xC0000135, вывода нет)");
            }
        }

        // ------------------------------------------------------------------ скрипты тестов

        private static void ScriptsCoverAllSuites()
        {
            var suites = Regex.Matches(Read("tests/CMakeLists.txt"), @"add_test\(NAME\s+(\w+?)_tests\b")
                              .Select(m => m.Groups[1].Value).ToList();
            Check(suites.Count >= 11, "tests/CMakeLists.txt: найдено наборов: " + suites.Count);

            Match sh = Regex.Match(Read("scripts/test-all.sh"), @"for\s+t\s+in\s+([^;]+);");
            Check(sh.Success, "test-all.sh: найден цикл по наборам");
            var inSh = sh.Success ? sh.Groups[1].Value.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries).ToList() : new List<string>();
            CheckEqual("", string.Join(", ", suites.Except(inSh)), "test-all.sh запускает все наборы (не хватает)");
            CheckEqual("", string.Join(", ", inSh.Except(suites)), "test-all.sh не запускает несуществующих наборов (лишние)");

            Match ps = Regex.Match(Read("scripts/test-all.ps1"), @"foreach\s*\(\s*\$t\s+in\s+([^)]+)\)");
            Check(ps.Success, "test-all.ps1: найден цикл по наборам");
            var inPs = ps.Success ? Regex.Matches(ps.Groups[1].Value, @"'(\w+)'").Select(m => m.Groups[1].Value).ToList() : new List<string>();
            CheckEqual("", string.Join(", ", suites.Except(inPs)), "test-all.ps1 запускает все наборы (не хватает)");
            CheckEqual("", string.Join(", ", inPs.Except(suites)), "test-all.ps1 не запускает несуществующих наборов (лишние)");
        }
    }
}
