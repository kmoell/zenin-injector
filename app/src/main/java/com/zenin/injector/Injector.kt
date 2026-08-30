package com.zenin.injector

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Ядро запуска zenin_loader от рута.
 *
 * Ключевые моменты:
 *  - MOUNT MASTER: на Xiaomi/HyperOS обычный `su` заходит в изолированный mount
 *    namespace, где не видно память/карты чужих процессов -> инжектор не находит
 *    Standoff 2. Поэтому запуск идёт через `su -mm` (mount master). Если версия su
 *    не поддерживает -mm, автоматически откатываемся на обычный `su`.
 *  - ЗАДЕРЖКА 15с: перед запуском loader ждём 15 секунд (с отсчётом в логе), чтобы
 *    игра точно успела прогрузиться в память. Loader стартует только после этого.
 *  - КЛЮЧ через stdin: ключ уходит в stdin root-скрипта, там читается в переменную
 *    и передаётся loader'у через printf. Нигде не пишется на диск и не светится в ps.
 */
object Injector {

    private const val ASSET_NAME = "zenin_loader.bin"
    private const val TMP_PATH = "/data/local/tmp/zenin_loader"
    private const val INJECT_DELAY_SEC = 15

    /**
     * Определяет рабочий вариант su. Сначала пробует mount master (`su -mm`),
     * затем обычный `su`. Возвращает массив аргументов su без "-c", или null если рута нет.
     */
    fun detectSu(): Array<String>? {
        val variants = listOf(
            arrayOf("su", "-mm"),
            arrayOf("su", "--mount-master"),
            arrayOf("su")
        )
        for (v in variants) {
            try {
                val proc = ProcessBuilder(*v, "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (out.contains("uid=0")) return v
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun run(ctx: Context, key: String, onLog: (String) -> Unit): Boolean {
        // 1. Распаковать бинарь из assets во внутреннее хранилище
        val localBin = File(ctx.filesDir, "zenin_loader")
        try {
            ctx.assets.open(ASSET_NAME).use { input ->
                localBin.outputStream().use { output ->
                    input.copyTo(output, 1 shl 16)
                }
            }
            localBin.setExecutable(true, false)
            onLog("[*] Бинарь распакован (${localBin.length()} байт)")
        } catch (e: Exception) {
            onLog("[x] Не удалось распаковать бинарь: ${e.message}")
            return false
        }

        // 2. Определить su (с mount master)
        val su = detectSu()
        if (su == null) {
            onLog("[x] Рут не найден (su недоступен).")
            return false
        }
        val mode = if (su.any { it == "-mm" || it == "--mount-master" }) "mount master" else "обычный"
        onLog("[+] Рут OK ($mode)")

        // 3. Root-скрипт: читаем ключ из stdin в переменную, кладём бинарь в tmp,
        //    ждём 15с с отсчётом, запускаем loader с ключом в его stdin.
        val internalPath = localBin.absolutePath
        val script = buildString {
            append("IFS= read -r ZKEY; ")                       // ключ из stdin скрипта
            append("cp '$internalPath' '$TMP_PATH' 2>/dev/null; ")
            append("chmod 755 '$TMP_PATH' 2>/dev/null; ")
            append("cd /data/local/tmp || exit 1; ")
            append("echo '[*] Инъекция через $INJECT_DELAY_SEC сек...'; ")
            append("sleep 5; echo '[*] 10...'; ")
            append("sleep 5; echo '[*] 5...'; ")
            append("sleep 5; echo '[*] Запуск инжектора...'; ")
            append("printf '%s\\n' \"\$ZKEY\" | ./zenin_loader 2>&1")
        }

        return try {
            val proc = ProcessBuilder(*su, "-c", script)
                .redirectErrorStream(true)
                .start()

            // Отдаём ключ в stdin скрипта (там его заберёт `read`)
            proc.outputStream.use { os ->
                os.write((key + "\n").toByteArray())
                os.flush()
            }

            // Стримим вывод построчно в лог
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            reader.useLines { lines ->
                for (line in lines) {
                    onLog(line)
                    if (line.contains("Success", ignoreCase = true) ||
                        line.contains("Injecting to PID", ignoreCase = true)
                    ) {
                        onLog("[+] Инъекция прошла.")
                    }
                }
            }
            onLog("[*] Loader завершил вывод.")
            true
        } catch (e: Exception) {
            onLog("[x] Ошибка запуска: ${e.message}")
            false
        }
    }
}
