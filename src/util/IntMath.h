// Арифметика int (64 бита со знаком) с ЗАВОРАЧИВАНИЕМ по модулю 2^64 — как в C# без checked и в Java:
// 9223372036854775807 + 1 == -9223372036854775808.
//
// Обычное `a + b` над long long при переполнении — неопределённое поведение C++ (компилятор вправе выкинуть проверки
// после него), а `LLONG_MIN / -1` и `LLONG_MIN % -1` на x86 ещё и убивают процесс сигналом SIGFPE. Здесь всё определено:
// сложение, вычитание, умножение и отрицание считаются в беззнаковых числах, деление на -1 — через отрицание.
#pragma once

namespace glass {

inline long long wrapAdd(long long a, long long b) {
    return static_cast<long long>(static_cast<unsigned long long>(a) + static_cast<unsigned long long>(b));
}

inline long long wrapSub(long long a, long long b) {
    return static_cast<long long>(static_cast<unsigned long long>(a) - static_cast<unsigned long long>(b));
}

inline long long wrapMul(long long a, long long b) {
    return static_cast<long long>(static_cast<unsigned long long>(a) * static_cast<unsigned long long>(b));
}

inline long long wrapNeg(long long a) {
    return static_cast<long long>(0ull - static_cast<unsigned long long>(a));
}

// Делитель b != 0 проверяет вызывающий (деление на ноль — ошибка выполнения, а не заворачивание).
inline long long wrapDiv(long long a, long long b) { return b == -1 ? wrapNeg(a) : a / b; }

inline long long wrapMod(long long a, long long b) { return b == -1 ? 0 : a % b; }

}  // namespace glass
