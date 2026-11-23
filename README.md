<p align="center"><img src="assets/glass-icon-512.png" width="112" alt="Значок Glass"></p>

# Glass

[![CI](https://github.com/sxansee/glass-lang/actions/workflows/ci.yml/badge.svg)](https://github.com/sxansee/glass-lang/actions/workflows/ci.yml)
[![Лицензия: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Версия 1.0.0](https://img.shields.io/badge/version-1.0.0-brightgreen.svg)](CHANGELOG.md)

> **In English.** Glass is a small statically typed object-oriented teaching language. Its built-in introspection operators
> (`~layout`, `~vtable`, `~address`, `~heap`, `~gc`) show what other languages hide: how an object is laid out in memory,
> how a virtual call picks its method, and what the garbage collector does. This repository holds the compiler and a bytecode VM
> with a mark-and-sweep GC (C++17), a C API, and Glass Studio — a Windows IDE (WPF) with a step debugger, a live heap graph,
> an object inspector and a built-in course. The documentation is in Russian.

**Glass — учебный язык программирования, который не прячет, что происходит внутри.** В большинстве языков объект «просто лежит
где-то в памяти», виртуальный вызов «просто работает», а мусор «просто исчезает». В Glass всё это можно увидеть: в язык встроены
операторы интроспекции, а среда Glass Studio рисует кучу, стек вызовов и таблицу виртуальных методов прямо во время выполнения.

Язык рассчитан на студентов, которые изучают ООП и устройство компиляторов. Синтаксис свой — `mold` и `facet` вместо `class` и
`interface`, `fn` с типом после имени, без `new`, без обязательных `;`, — а семантика проверенная, как в C# и Java: статическая
типизация, наследование, виртуальные методы через vtable, контракты-интерфейсы, сборка мусора.

## Что внутри

- **Компилятор и виртуальная машина** (C++17, без внешних зависимостей): лексер с автоматической расстановкой `;`, парсер,
  семантический анализ, проверка типов в телах функций, генерация байткода, стековая VM с настоящей виртуальной диспетчеризацией
  и сборщиком мусора mark-and-sweep.
- **Интроспекция как часть языка**: `~layout`, `~vtable`, `~address`, `~heap`, `~gc`.
- **Glass Studio** (Windows, WPF): редактор, пошаговый отладчик, живая схема кучи, инспектор объектов, подсказки по языку и курс
  из 24 уроков с автоматической проверкой заданий.
- **C API** (`glass_capi`): тот же компилятор и VM как библиотека. На ней построена Studio, через неё Glass можно встроить в свою программу.
- **Документация**, в которой каждый пример исполняется настоящей VM в тестах, и шпаргалка для показа.

## Быстрый старт

**Windows — Glass Studio.** Скачайте `GlassStudio-1.0.0-win-x64.zip` со страницы [Releases](https://github.com/sxansee/glass-lang/releases),
распакуйте и запустите `GlassStudio\GlassStudio.exe` (устанавливать .NET не нужно). В меню **Файл → Примеры** лежат готовые программы:
**F5** запускает с отладкой, **Ctrl+F5** просто выполняет. В архиве есть и консольный `glassc.exe`, а также папки `examples` и `docs`.

**Linux, macOS, WSL — компилятор и VM.** Нужны CMake 3.16+ и компилятор с поддержкой C++17:

```
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
./build/glassc examples/shapes.glass
```

`glassc` без аргументов запускает интерактивный режим (REPL). Сборка Studio из исходников — в [docs/building.md](docs/building.md).

## Язык за пять минут

### Переменные, типы, функции

Типы статические, но там, где компилятор выведет их сам, писать необязательно. Деление целых — нацело, `7 / 2.0` — уже вещественное.

```glass run
fn main() {
    let name = "Glass"
    let year: int = 2026
    print("Привет,", name, year)
    print(7 / 2, 7 % 2, 7 / 2.0)
}
```

```output
Привет, Glass 2026
3 1 3.5
```

### Объекты, наследование, виртуальный вызов

`mold` — тип объекта (аналог `class`), объекты создаются без `new`: `Dog("Рекс")`. Ключевые слова `virtual` и `override` включают
переопределение. Какой `speak()` вызвать, решает таблица виртуальных методов самого объекта, а не тип переменной, — и эту таблицу можно увидеть (`~vtable`, ниже).

```glass run
mold Animal {
    let name: string

    Animal(name: string) {
        this.name = name
    }

    virtual fn speak() -> string {
        return "..."
    }
}

mold Dog : Animal {
    Dog(name: string) {
        super(name)
    }

    override fn speak() -> string {
        return "Гав!"
    }
}

fn describe(a: Animal) {
    print(a.name, "говорит", a.speak())
}

fn main() {
    describe(Animal("Нечто"))
    describe(Dog("Рекс"))
}
```

```output
Нечто говорит ...
Рекс говорит Гав!
```

### facet — контракт без реализации

`facet` — список методов, которые обязан реализовать `mold`. Через переменную типа `facet` вызывают метод, не зная конкретного типа.

```glass run
facet Shape {
    fn area() -> double
}

mold Circle : Shape {
    let r: double

    Circle(r: double) {
        this.r = r
    }

    fn area() -> double {
        return PI * r * r
    }
}

mold Square : Shape {
    let side: double

    Square(side: double) {
        this.side = side
    }

    fn area() -> double {
        return side * side
    }
}

fn main() {
    let shapes: [Shape] = [Circle(1.0), Square(2.0)]
    for s in shapes {
        print(floor(s.area() * 100) / 100.0)
    }
}
```

```output
3.14
4.0
```

### Исключения

`throw`, `try` / `catch` / `finally` и свои типы исключений — потомки `Exception`; ветка `catch` выбирается по типу.

```glass run
mold NegativeError : Exception {
    NegativeError(msg: string) {
        super(msg)
    }
}

fn root(x: double) -> double {
    if x < 0 {
        throw NegativeError("корень из отрицательного: " + str(x))
    }
    return sqrt(x)
}

fn main() {
    try {
        print(root(16.0))
        print(root(-1.0))
    } catch e: NegativeError {
        print("поймали:", e.message)
    } finally {
        print("готово")
    }
}
```

```output
4.0
поймали: корень из отрицательного: -1.0
готово
```

### Массивы и строки

Массивы типизированы, у них есть методы (`sort`, `append`, `pop`, …) и срезы `[от:до:шаг]`; строки неизменяемы, у них тоже есть методы.

```glass run
fn main() {
    let xs = [5, 3, 8, 1]
    xs.sort()
    print(xs, sum(xs), xs[1:3])
    let words = "glass is small".split(" ")
    print(len(words), words[0].upper())
}
```

```output
[1, 3, 5, 8] 17 [3, 5]
3 GLASS
```

Остальное — `while`, `for … in`, `break` / `continue`, `static`-члены, модификаторы `public` / `private` / `protected`,
преобразования типов — на страницах [описания языка](docs/README.md).

## Интроспекция: то, что другие языки прячут

Пять операторов — часть языка, а не отладчика: они работают в обычной программе.

| Оператор | Что показывает |
|---|---|
| `объект~layout` | раскладку полей в памяти: имя, смещение (в слотах), размер; поля предка идут первыми |
| `объект~vtable` | таблицу виртуальных методов: какая реализация вызовется для каждого `virtual`-метода |
| `объект~address` | адрес объекта в куче как целое число (`hex(…)` покажет его как `0x…`) |
| `~heap` | снимок всей кучи: все объекты, которые ещё не собраны |
| `~gc` | запускает сборку мусора немедленно |

```glass run
mold Animal {
    let name: string
    let age: int

    Animal(name: string, age: int) {
        this.name = name
        this.age = age
    }

    virtual fn speak() -> string {
        return "..."
    }
}

mold Dog : Animal {
    let breed: string

    Dog(name: string, age: int, breed: string) {
        super(name, age)
        this.breed = breed
    }

    override fn speak() -> string {
        return "Гав!"
    }
}

fn main() {
    let d = Dog("Rex", 3, "Labrador")
    print(d~layout)
    print(d~vtable)
    print(hex(d~address))
    ~gc
    print(~heap)
}
```

```output
layout(Dog): name@0 (8 байт), age@1 (8 байт), breed@2 (8 байт)
vtable(Dog): speak -> Dog.speak
<адрес>
heap(1 объект(ов)): Dog@<адрес>
```

Результаты `~layout`, `~vtable` и `~heap` — тоже объекты кучи: до ближайшего `~gc` следующий `~heap` покажет и их, так что наблюдение
оставляет след. Подробнее — в разделе [«Интроспекция»](docs/language/introspection.md).

## Glass Studio

Среда для Windows (WPF) показывает то же самое графически и по шагам:

- **Отладчик.** Точки останова (`F9`), шаг с обходом (`F10`), с заходом (`F11`), с выходом (`Shift+F11`), стек вызовов и переменные.
- **Куча.** Схема объектов и ссылок: корни (локальные переменные и `static`-поля), стрелки между объектами. Объекты, на которые
  никто не ссылается, подсвечены как мусор; кнопка «Собрать мусор» делает то же, что `~gc` в коде.
- **Инспектор объекта.** Поля со смещениями, цепочка предков, vtable («кто реально реализует»), адрес.
- **Редактор.** Подсветка, номера строк, поиск (`Ctrl+F`), размер шрифта для проектора (`Ctrl+колесо`), карточка элемента кода
  по `F1` или правой кнопке: что это, пример, ссылка на документацию.
- **Светлая и тёмная темы** («Вид → Тема», в том числе «как в системе»): перекрашивается всё — от редактора и схемы кучи до заголовка окна.
- **Курс «Glass с нуля».** Шесть глав и 24 урока с заданиями и автоматической проверкой.
- **Журнал** работы приложения; все данные лежат в `%LOCALAPPDATA%\GlassStudio`.

Как показать всё это на защите или лекции — в [шпаргалке](docs/demo.md): четыре сценария, что нажимать и что при этом видно.
Подробно о каждой панели — в [описании Studio](docs/ide.md).

## Как это устроено

```
исходник ─► Lexer ─► Parser ─► Sema ─► BodyChecker ─► CodeGen ─► VM + GC
            токены   AST      объявления  типы тел      байткод    стековая машина, vtable,
            (с ASI)           и контракты функций                  mark-and-sweep
                                                           │
                        C API (glass_capi) ◄───────────────┘
                              │
                  Glass.Interop (.NET) ─► Glass.Studio.Core ─► Glass Studio (WPF)
```

Как устроены раскладка полей, vtable, кадры вызовов, байткод и сборщик мусора — в [«Архитектуре»](docs/architecture.md).

## Структура репозитория

```
glass/
├── src/              компилятор и VM (C++17): lexer, parser, ast, sema, codegen, bytecode, vm, prelude, capi, repl, util
├── tests/            тесты ядра (11 наборов на C++)
├── dotnet/           Glass Studio: Glass.Studio.Core (логика без WPF), Glass.Interop (P/Invoke), Glass.Studio (WPF),
│                     Glass.Studio.Tests (тесты против настоящей библиотеки)
├── examples/         примеры программ на Glass (запускаются в тестах)
├── assets/           значок Glass: SVG-исходники, PNG и .ico (значок GlassStudio.exe)
├── docs/             документация: язык, Studio, курс, C API, архитектура, шпаргалка для показа
├── scripts/          test-all.sh и test-all.ps1 (сборка и все тесты), update-docs.py, fuzz.py
├── .github/          CI и выпуск релизов (GitHub Actions), шаблоны issue и pull request
├── glass-grammar.md  формальная грамматика языка
└── CHANGELOG.md · RELEASING.md · CONTRIBUTING.md · LICENSE · THIRD-PARTY-NOTICES.md
```

## Сборка и тесты

Для ядра нужны CMake 3.16+ и компилятор C++17 (GCC, Clang, MSVC); для Studio и .NET-тестов — .NET SDK 8 (окно WPF собирается только на Windows).

```
# Linux, macOS, WSL: сборка и все тесты (C++ и .NET против настоящей библиотеки)
bash scripts/test-all.sh
```

```
# Windows, PowerShell
.\scripts\test-all.ps1
```

Тестов больше тысячи на C++ и больше двух тысяч на .NET. Примеры из документации и из этого файла исполняются настоящей VM в тестах, а
справочник по языку генерируется из тех же записей, что показывают подсказки в Studio, — поэтому документация не расходится с языком.
Ядро проверялось под AddressSanitizer и UndefinedBehaviorSanitizer и мутационным фаззингом (`scripts/fuzz.py`) — как повторить, описано в
[docs/building.md](docs/building.md).

## Что пока не входит в версию 1.0

- дженерики (`mold Box<T>`: синтаксис разбирается, но компилятор сразу сообщает, что они не поддерживаются);
- `switch`, тернарный оператор `?:`, лямбды, оператор `in` (для проверки вхождения есть метод `contains`), интерполяция строк, ввод с клавиатуры;
- перехват ошибок самой VM: деление на ноль, выход за границы массива, неверное приведение останавливают программу, а `try` / `catch` ловит только исключения-объекты;
- `return`, `break` и `continue` внутри `try` с `finally`;
- Glass Studio работает только на Windows; компилятор и VM — на любой платформе с C++17.

Список открытых задач — в [TODO.md](TODO.md), история изменений — в [CHANGELOG.md](CHANGELOG.md).

## Документация

Всё — в папке [docs](docs/README.md):

- **Язык:** [основы](docs/language/basics.md), [управление потоком](docs/language/control-flow.md), [функции](docs/language/functions.md),
  [строки](docs/language/strings.md), [массивы](docs/language/arrays.md), [объекты](docs/language/objects.md),
  [исключения](docs/language/exceptions.md), [интроспекция](docs/language/introspection.md); [справочник](docs/reference/language-reference.md)
  из записей по ключевым словам, типам, встроенным функциям и методам; [формальная грамматика](glass-grammar.md).
- **Среда:** [Glass Studio](docs/ide.md), [курс](docs/course.md), [шпаргалка для показа](docs/demo.md), [журнал](docs/logging.md).
- **Устройство:** [архитектура](docs/architecture.md), [C API](docs/c-api.md), [сборка и тесты](docs/building.md).

## Участие и лицензия

Об ошибках и предложениях пишите в [Issues](https://github.com/sxansee/glass-lang/issues). Как собрать проект, прогнать тесты и оформить
pull request — в [CONTRIBUTING.md](CONTRIBUTING.md); как выпускается версия — в [RELEASING.md](RELEASING.md).
Лицензия — MIT ([LICENSE](LICENSE)); сторонние компоненты (редактор AvalonEdit) — в [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
