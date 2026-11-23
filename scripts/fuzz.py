#!/usr/bin/env python3
"""Мутационный фаззинг компилятора и VM Glass: портит настоящие программы и смотрит, не падает ли glassc.

Берёт примеры из examples/ и блоки ```glass из docs/, случайно меняет их (вырезает и дублирует куски, подставляет токены,
обрубает, портит байты) и запускает на каждом мутанте `glassc`. Ошибка компиляции или выполнения — нормальный исход.
Плохой исход один: процесс убит сигналом, сработал AddressSanitizer / UBSan или «terminate called» (неперехваченное
исключение C++). Тогда мутант сохраняется в папку для воспроизведения, а скрипт завершается с кодом 1.

Лучше всего запускать на сборке с санитайзерами (см. docs/building.md):

    python3 scripts/fuzz.py build-asan/glassc 2000 1 [папка-для-падений]

Аргументы: путь к glassc, число мутантов (2000), начальное значение генератора (1), папка для падений (временная).
"""
import concurrent.futures as cf
import glob
import os
import pathlib
import random
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
TIMEOUT = 4  # секунд на мутанта: программа с бесконечным циклом — не падение, её просто пропускаем

POOL = ['{', '}', '(', ')', '[', ']', '~', '.', ',', ';', ':', '->', '=', '==', '!=', '<', '>', '<=', '>=', '+', '-', '*', '/', '%',
        '!', '&&', '||', 'fn ', 'mold ', 'facet ', 'let ', 'return ', 'if ', 'else ', 'while ', 'for ', 'in ', 'try ', 'catch ',
        'finally ', 'throw ', 'this', 'super', 'static ', 'override ', 'virtual ', 'public ', 'private ', 'protected ', 'null',
        'true', 'false', 'int', 'string', 'double', 'bool', '"', '\\', '//', '/*', '*/', '0', '1', '-1', '9223372036854775807',
        '1e999', '"a"', '[1,2]', '[]', '..', '::', '@', '#', '$', '`', '~heap', '~gc', '~layout', '~vtable', '~address', 'main',
        'print(', 'len(', 'range(', '<T>']


def load_corpus():
    corpus = [pathlib.Path(f).read_text(encoding='utf-8') for f in glob.glob(str(ROOT / 'examples' / '*.glass'))]
    for f in glob.glob(str(ROOT / 'docs' / '**' / '*.md'), recursive=True):
        text = pathlib.Path(f).read_text(encoding='utf-8')
        corpus += [m.group(1) for m in re.finditer(r'```glass(?: run| error)?\n(.*?)```', text, re.S)]
    return [c for c in corpus if 'fn main' in c]


def mutate(rng, s):
    for _ in range(rng.randint(1, 4)):
        if not s:
            s = 'fn main() {}'
        k, n = rng.random(), len(s)
        i = rng.randrange(n)
        if k < 0.2:
            s = s[:i] + s[i + rng.randint(1, 20):]                       # вырезать кусок
        elif k < 0.3:
            j = min(n, i + rng.randint(1, 30))
            s = s[:j] + s[i:j] + s[j:]                                   # продублировать кусок
        elif k < 0.55:
            s = s[:i] + rng.choice(POOL) + s[i + rng.randint(0, 2):]     # заменить токеном
        elif k < 0.7:
            s = s[:i] + rng.choice(POOL) + s[i:]                         # вставить токен
        elif k < 0.8:
            lines = s.split('\n')
            if len(lines) > 2:
                a, b = rng.randrange(len(lines)), rng.randrange(len(lines))
                lines[a], lines[b] = lines[b], lines[a]                  # поменять строки местами
                s = '\n'.join(lines)
        elif k < 0.9:
            s = s[:i]                                                    # обрубить
        else:
            s = s[:i] + chr(rng.choice([0, 1, 127, 0x80, 0xFF, 0x2028, 0x1F600])) + s[i:]   # странный символ
    return s


def verdict(returncode, stderr):
    """Возвращает описание падения или None, если исход нормальный."""
    if 'AddressSanitizer' in stderr or 'LeakSanitizer' in stderr:
        m = re.search(r'((?:Address|Leak)Sanitizer[^\n]{0,60})', stderr)
        return 'sanitizer: ' + (m.group(1) if m else 'AddressSanitizer')
    m = re.search(r'([^\s:]+:\d+):\d+: runtime error: ([^\n]{0,60})', stderr)
    if m:
        return 'ubsan: ' + m.group(0)[-110:]
    if 'terminate called' in stderr:
        return 'terminate: ' + stderr.split('terminate called', 1)[1].strip().splitlines()[0][:80]
    if returncode < 0:
        return f'сигнал {-returncode}'
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    binary = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 2000
    seed = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    out = pathlib.Path(sys.argv[4] if len(sys.argv) > 4 else tempfile.mkdtemp(prefix='glass-fuzz-'))
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    corpus = load_corpus()
    jobs = [(i, mutate(rng, rng.choice(corpus))) for i in range(count)]

    def work(job):
        idx, src = job
        path = out / f'm{idx}.glass'
        path.write_bytes(src.encode('utf-8', 'surrogatepass'))
        try:
            r = subprocess.run([binary, str(path)], capture_output=True, timeout=TIMEOUT, stdin=subprocess.DEVNULL)
        except subprocess.TimeoutExpired:
            path.unlink(missing_ok=True)
            return 'timeout', None, idx
        bad = verdict(r.returncode, r.stderr.decode('utf-8', 'replace'))
        if bad:
            return 'crash', bad, idx
        path.unlink(missing_ok=True)
        return 'ok', None, idx

    stats = {'ok': 0, 'timeout': 0, 'crash': 0}
    crashes = {}
    with cf.ThreadPoolExecutor(max_workers=max(1, os.cpu_count() or 1)) as pool:
        for kind, bad, idx in pool.map(work, jobs):
            stats[kind] += 1
            if bad:
                crashes.setdefault(bad, []).append(idx)
    print(f'мутантов: {count}, без падений: {stats["ok"]}, зависших (пропущены): {stats["timeout"]}, падений: {stats["crash"]}')
    for bad, ids in sorted(crashes.items(), key=lambda kv: -len(kv[1])):
        print(f'  {len(ids):4}  {bad}   (пример: {out / ("m%d.glass" % ids[0])})')
    return 1 if crashes else 0


if __name__ == '__main__':
    sys.exit(main())
