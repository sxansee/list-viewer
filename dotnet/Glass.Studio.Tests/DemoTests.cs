using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Glass.Studio.Examples;
using Glass.Studio.ViewModels;
using static Glass.Studio.Tests.TestKit;

namespace Glass.Studio.Tests
{
    /// <summary>
    /// Сценарии показа из docs/demo.md — шаг за шагом, на настоящей VM и настоящей MainViewModel: те же клавиши
    /// (F5 / F9 / F10 / F11 — это StartOrContinue / ToggleBreakpoint / StepOver / StepInto), что нажимает автор
    /// в окне, и то, что он при этом должен увидеть (стек, переменные, инспектор, схема кучи, вывод).
    /// Если шпаргалка и поведение разойдутся, упадёт один из этих тестов. Цвета и анимация — только глазами на Windows.
    /// </summary>
    public static class DemoTests
    {
        public static void Register()
        {
            Add("демо: сценарий 1 — виртуальный вызов (shapes)", VirtualCall);
            Add("демо: сценарий 2 — цепочка в куче (linked_list)", LinkedList);
            Add("демо: сценарий 3 — сборка мусора (gc_demo), шаги F10 и кнопка", GarbageCollection);
            Add("демо: сценарий 4 — интроспекция из кода (introspection) и инспектор", Introspection);
        }

        private static MainViewModel Open(string id, out FakeEditor editor)
        {
            editor = new FakeEditor();
            var vm = new MainViewModel(editor, new FakeDialogs(), liveCheckDelayMs: -1);
            vm.OpenExample(ExampleCatalog.All.First(e => e.Id == id));
            return vm;
        }

        /// <summary>Номер строки по её тексту (без отступа) — шпаргалка называет строки так же, а не номерами.</summary>
        private static int LineOf(FakeEditor editor, string trimmed)
        {
            int line = editor.Text.Split('\n').ToList().FindIndex(l => l.Trim() == trimmed) + 1;
            Check(line > 0, "в примере есть строка «" + trimmed + "»");
            return line;
        }

        private static string[] OutputLines(MainViewModel vm) =>
            vm.Output.Replace("\r\n", "\n").TrimEnd('\n').Split('\n');

        // ------------------------------------------------------------------ 1. виртуальный вызов

        private static async Task VirtualCall()
        {
            MainViewModel vm = Open("shapes", out FakeEditor ed);
            int areaLine = LineOf(ed, "print(s.area())");
            int circleCall = LineOf(ed, "printArea(c)");
            int squareCall = LineOf(ed, "printArea(sq)");
            vm.ToggleBreakpoint(areaLine);

            // F5: первая остановка — printArea(c); в «Переменных» у s тип — настоящий класс объекта, а не Shape.
            await vm.StartOrContinueAsync();
            CheckEqual(IdeState.Paused, vm.State, "F5: остановились на точке");
            CheckEqual(areaLine, ed.ExecutionLine, "на строке print(s.area())");
            CheckEqual("printArea,main", string.Join(",", vm.CallStack.Select(f => f.Function)), "стек: printArea ← main");
            CheckEqual(circleCall, vm.CallStack[1].Line, "main вызвал printArea(c)");
            LocalItem s = vm.Locals.First(l => l.Name == "s");
            CheckEqual("Circle", s.TypeText, "s — Circle (объявлен как Shape)");

            // Клик по s: инспектор показывает vtable и «кто реально реализует».
            vm.SelectedLocal = s;
            CheckEqual("Circle", vm.Inspector.Title, "инспектор: Circle");
            CheckEqual("Circle → Shape", vm.Inspector.AncestryText, "предки: Circle → Shape");
            VTableRow row = vm.Inspector.VTable.Single();
            CheckEqual("area", row.Method, "в vtable метод area");
            CheckEqual("Circle", row.ImplementedBy, "реализует Circle");

            // F11: вошли в Circle.area — в стеке видно, какая реализация выполняется.
            await vm.StepIntoAsync();
            CheckEqual("Circle.area,printArea,main", string.Join(",", vm.CallStack.Select(f => f.Function)), "F11: стек Circle.area ← printArea ← main");

            // F5: вторая остановка — уже printArea(sq), и ТОЛЬКО она: возврат из area() в остаток строки второй остановкой не считается.
            await vm.StartOrContinueAsync();
            CheckEqual(IdeState.Paused, vm.State, "F5: снова на точке");
            CheckEqual(areaLine, ed.ExecutionLine, "на той же строке print(s.area())");
            CheckEqual(squareCall, vm.CallStack[1].Line, "но main уже вызвал printArea(sq), а не повторил printArea(c)");
            s = vm.Locals.First(l => l.Name == "s");
            CheckEqual("Square", s.TypeText, "теперь s — Square");
            vm.SelectedLocal = s;
            CheckEqual("Square", vm.Inspector.VTable.Single().ImplementedBy, "vtable: area реализует Square");

            await vm.StepIntoAsync();
            CheckEqual("Square.area", vm.CallStack[0].Function, "F11: вошли в Square.area");

            // F5: больше остановок нет — программа доработала.
            await vm.StartOrContinueAsync();
            CheckEqual(IdeState.Finished, vm.State, "F5: программа завершилась");
            CheckEqual("27.0,16.0", string.Join(",", OutputLines(vm)), "вывод: площадь круга и квадрата");
        }

        // ------------------------------------------------------------------ 2. цепочка в куче

        private static async Task LinkedList()
        {
            MainViewModel vm = Open("linked_list", out FakeEditor ed);
            int callLine = LineOf(ed, "print(length(head))");
            int headerLine = LineOf(ed, "fn length(head: Node) -> int {");
            int whileLine = LineOf(ed, "while cur != null {");
            vm.ToggleBreakpoint(callLine);

            // F5: четыре узла в четырёх колонках, три стрелки next от узла к узлу, мусора нет.
            await vm.StartOrContinueAsync();
            CheckEqual(callLine, ed.ExecutionLine, "F5: остановились на print(length(head))");
            CheckEqual(4, vm.HeapGraph.Nodes.Count, "на схеме четыре узла");
            CheckEqual(4, vm.HeapGraph.Nodes.Select(n => n.X).Distinct().Count(), "в четырёх колонках — цепочка");
            CheckEqual(3, vm.HeapGraph.Edges.Count(e => !e.IsFromRoot), "три стрелки next между узлами");
            CheckEqual(0, vm.HeapGraph.GarbageCount, "мусора нет");
            List<string> chain = vm.HeapGraph.Nodes.Select(n => n.Address).ToList();

            // F11: вход в length — остановка на заголовке, параметр head уже связан.
            await vm.StepIntoAsync();
            CheckEqual(headerLine, ed.ExecutionLine, "F11: заголовок length");
            CheckEqual("length,main", string.Join(",", vm.CallStack.Select(f => f.Function)), "стек: length ← main");
            string headAddress = vm.Locals.First(l => l.Name == "head").Address!;

            // Дальше F11 по строкам: при каждом возвращении к `while` переменная cur указывает на следующий узел списка.
            var curAtLoop = new List<string?>();
            var stepsBetween = new List<int>();   // сколько нажатий F11 между двумя приходами к `while`
            int presses = 0, lastArrival = -1;
            for (int i = 0; i < 40 && vm.State == IdeState.Paused && vm.CallStack.Count == 2; i++)
            {
                await vm.StepIntoAsync();
                presses++;
                if (vm.CallStack.Count == 2 && ed.ExecutionLine == whileLine)
                {
                    if (lastArrival >= 0) stepsBetween.Add(presses - lastArrival);
                    lastArrival = presses;
                    string? cur = vm.Locals.First(l => l.Name == "cur").Address;
                    curAtLoop.Add(cur);
                    // На схеме то же самое видно как корень «length · cur», который переезжает с узла на узел.
                    if (cur != null) Check(vm.HeapGraph.Roots.Any(r => r.Label == "length · cur" && r.TargetAddress == cur), "на схеме корень «length · cur» указывает на этот узел");
                }
            }
            CheckEqual(5, curAtLoop.Count, "к условию цикла пришли 5 раз: по числу узлов и ещё раз — выйти");
            CheckEqual("3,3,3,3", string.Join(",", stepsBetween), "один проход цикла — три нажатия F11 (while, count += 1, cur = cur.next)");
            CheckEqual(4, curAtLoop.Take(4).Distinct().Count(), "четыре разных узла");
            CheckEqual(headAddress, curAtLoop[0], "первым — head");
            Check(curAtLoop.Take(4).All(a => a != null && chain.Contains(a)), "все четыре — узлы с той самой схемы");
            CheckEqual<string?>(null, curAtLoop[4], "в конце cur пуст (null)");
        }

        // ------------------------------------------------------------------ 3. сборка мусора

        private static async Task GarbageCollection()
        {
            // Путь А: F10 по строкам. Первое нажатие просто входит в main (строка заголовка), дальше — по строке за нажатие.
            MainViewModel vm = Open("gc_demo", out FakeEditor ed);
            int gcLine = LineOf(ed, "~gc");
            int printLine = LineOf(ed, "print(~heap)");
            for (int i = 0; i < 5; i++) await vm.StepOverAsync();
            CheckEqual(gcLine, ed.ExecutionLine, "F10 ×5: стоим на строке ~gc — `a = b` уже выполнено");
            CheckEqual(2, vm.HeapGraph.Nodes.Count, "в куче два Box");
            CheckEqual(1, vm.HeapGraph.GarbageCount, "и один из них — мусор (на схеме красный)");
            var garbage = vm.HeapGraph.Nodes.Single(n => n.IsGarbage);
            Check(vm.Locals.All(l => l.Address != garbage.Address), "на мусорный узел не указывает ни одна переменная");

            await vm.StepOverAsync();   // выполнили ~gc
            CheckEqual(printLine, ed.ExecutionLine, "F10: ~gc выполнена, стоим на print(~heap)");
            CheckEqual(1, vm.HeapGraph.Nodes.Count, "красный узел исчез");
            CheckEqual(0, vm.HeapGraph.GarbageCount, "мусора нет");

            // Дальше идти не нужно: сам снимок ~heap — объекты кучи (HeapSnapshot и массивы), и на последнем шаге они покраснеют.
            await vm.StepOverAsync();
            Check(vm.HeapGraph.GarbageCount > 0, "после print(~heap) на схеме появляются красные узлы — это сам снимок");
            Check(vm.HeapGraph.Nodes.Any(n => n.IsGarbage && n.Title == "HeapSnapshot"), "среди них HeapSnapshot");

            // Путь Б: F5 до ~gc и кнопка «Собрать мусор» (то же, что ~gc в коде).
            MainViewModel vm2 = Open("gc_demo", out FakeEditor ed2);
            vm2.ToggleBreakpoint(LineOf(ed2, "~gc"));
            await vm2.StartOrContinueAsync();
            CheckEqual(1, vm2.HeapGraph.GarbageCount, "F5 до ~gc: один объект уже мусор");
            int freed = vm2.CollectGarbage();
            CheckEqual(1, freed, "кнопка «Собрать мусор»: освобождён один объект");
            CheckEqual(0, vm2.HeapGraph.GarbageCount, "красный узел исчез");
            CheckEqual(1, vm2.HeapGraph.Nodes.Count, "остался один Box");
        }

        // ------------------------------------------------------------------ 4. интроспекция

        private static async Task Introspection()
        {
            MainViewModel vm = Open("introspection", out FakeEditor ed);
            vm.ToggleBreakpoint(LineOf(ed, "~gc"));
            await vm.StartOrContinueAsync();
            CheckEqual(IdeState.Paused, vm.State, "F5: остановились перед ~gc");

            string[] lines = OutputLines(vm);
            CheckEqual(5, lines.Length, "к этому моменту напечатано пять строк");
            Check(lines[0].StartsWith("heap(1 объект(ов)): Dog@0x", StringComparison.Ordinal), "1) снимок до интроспекции: один объект — Dog");
            Check(long.TryParse(lines[1], out long address), "2) ~address — обычное целое число");
            CheckEqual("layout(Dog): name@0 (8 байт), age@1 (8 байт), breed@2 (8 байт)", lines[2], "3) ~layout: унаследованные поля первыми");
            CheckEqual("vtable(Dog): speak -> Dog.speak", lines[3], "4) ~vtable: speak реализует Dog");
            Check(lines[4].StartsWith("heap(", StringComparison.Ordinal) && !lines[4].StartsWith("heap(1 ", StringComparison.Ordinal),
                  "5) новый снимок: объектов больше одного");
            CheckContains(lines[4], "LayoutInfo@0x", "в нём результат ~layout");
            CheckContains(lines[4], "VTableInfo@0x", "и результат ~vtable");

            // Инспектор (клик по d) показывает то же самое, что напечатали ~layout, ~vtable и ~address.
            vm.SelectedLocal = vm.Locals.First(l => l.Name == "d");
            ObjectInspectorViewModel insp = vm.Inspector;
            CheckEqual(lines[2], "layout(Dog): " + string.Join(", ", insp.Fields.Select(f => f.Name + "@" + f.Offset + " (" + f.Size + " байт)")),
                       "инспектор: раскладка совпала с выводом ~layout");
            CheckEqual(lines[3], "vtable(Dog): " + string.Join(", ", insp.VTable.Select(r => r.Method + " -> " + r.Target)),
                       "инспектор: vtable совпала с выводом ~vtable");
            CheckContains(insp.AddressText, address.ToString(), "инспектор: то же число, что вернул ~address");

            // На схеме те самые служебные объекты — красные: на них никто не ссылается. «Собрать мусор» — остаётся один Dog.
            Check(vm.HeapGraph.GarbageCount > 0, "на схеме есть красные узлы — следы интроспекции");
            foreach (string trace in new[] { "LayoutInfo", "VTableInfo", "HeapSnapshot" })
                Check(vm.HeapGraph.Nodes.Any(n => n.IsGarbage && n.Title == trace), "среди красных узлов есть " + trace);
            Check(vm.HeapGraph.Nodes.Any(n => n.IsGarbage && n.IsArray), "и массивы внутри них");
            vm.CollectGarbage();
            CheckEqual(1, vm.HeapGraph.Nodes.Count, "после сборки на схеме один Dog");

            // F5: программа доработала; последний снимок — снова один Dog.
            await vm.StartOrContinueAsync();
            CheckEqual(IdeState.Finished, vm.State, "F5: программа завершилась");
            string last = OutputLines(vm).Last();
            Check(last.StartsWith("heap(1 объект(ов)): Dog@0x", StringComparison.Ordinal), "последний снимок: снова только Dog");
        }
    }
}
