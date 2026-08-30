package com.zenin.injector

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Ядро запуска zenin_loader от рута.
 *
 * Схема (как Ися гоняет вручную ./zenin_loader.sh + ввод ключа):
 *   1. Вшитый бинарь лежит в assets/zenin_loader.bin (не сжат — noCompress "bin").
 *   2. Распаковываем во внутреннюю папку приложения, затем root-копией кладём
 *      в /data/local/tmp/zenin_loader (переживает и очистку кэша, и переустановку
 *      мы кладём заново при каждом запуске — файл в tmp может пропасть, поэтому
 *      всегда обновляем из встроенной копии).
 *   3. Запускаем от рута: `echo КЛЮЧ | ./zenin_loader` — ключ уходит в stdin,
 *      бинарь читает его первым fgets и расшифровывает вшитую libZeninInternal.so.
 *   4. Loader сам ждёт Standoff 2 в лобби и инжектит. Его stdout/stderr стримим в лог.
 */
object Injector {

    private const val ASSET_NAME = "zenin_loader.bin"
    private const val TMP_PATH = "/data/local/tmp/zenin_loader"

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

        // 2. Проверить рут
        if (!hasRoot()) {
            onLog("[x] Рут не найден (su недоступен).")
            return false
        }
        onLog("[+] Рут OK")

        // 3. Собрать root-скрипт: скопировать бинарь в tmp, chmod, запустить с ключом в stdin.
        //    Ключ передаём через переменную окружения ZKEY и echo в stdin, чтобы он
        //    не светился в списке аргументов процесса (ps).
        val internalPath = localBin.absolutePath
        val script = buildString {
            append("cp '$internalPath' '$TMP_PATH' 2>/dev/null; ")
            append("chmod 755 '$TMP_PATH' 2>/dev/null; ")
            append("cd /data/local/tmp; ")
            // ключ в stdin через printf (без завершающих пробелов), без хранения в argv
            append("printf '%s\\n' \"\$ZKEY\" | ./zenin_loader 2>&1")
        }

        return try {
            val proc = ProcessBuilder("su", "-c", script)
                .apply { environment()["ZKEY"] = key }
                .redirectErrorStream(true)
                .start()

            // Стримим вывод построчно в лог
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            reader.useLines { lines ->
                for (line in lines) {
                    onLog(line)
                    // Как только loader сообщил об успехе — можно открывать игру,
                    // но loader продолжает работать (мониторит процесс), поэтому
                    // не блокируемся: продолжаем читать в фоне.
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

    private fun hasRoot(): Boolean {
        return try {
            val proc = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
}
