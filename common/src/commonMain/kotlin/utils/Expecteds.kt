package utils

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope

expect val backgroundScope : CoroutineScope
expect val mainScope: CoroutineScope
expect val clientEngine: HttpClientEngine
expect fun Float.format(precision: Int?): String
expect fun Long.format(): String
expect fun Int.format(): String
