package utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.logging.HttpLoggingInterceptor
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

actual val backgroundScope = Dispatchers.IO.createScope()
actual val mainScope = Dispatchers.Main.createScope()

actual val clientEngine = HttpClient(OkHttp){

    engine {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        addInterceptor(loggingInterceptor)
        config {
            readTimeout(30000, TimeUnit.MILLISECONDS)
            writeTimeout(30000, TimeUnit.MILLISECONDS)
            callTimeout(30000, TimeUnit.MILLISECONDS)
            connectTimeout(30000, TimeUnit.MILLISECONDS)
        }
    }

}.engine

private fun CoroutineDispatcher.createScope() = object : CoroutineScope {
    private val dispatcher = this@createScope
    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job
}

actual fun Float.format(precision: Int?): String {
    if (precision == null) {
        return this.toString()
    }
    return "%.${precision}f".format(this)
}

actual fun Long.format(): String {
    return NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(this)
}

actual fun Int.format(): String {
    return NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(this)
}
