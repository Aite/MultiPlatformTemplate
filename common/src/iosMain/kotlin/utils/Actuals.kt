package utils

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.ios.Ios
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.CoroutineContext

actual val clientEngine: HttpClientEngine by lazy { Ios.create() }


@kotlinx.coroutines.InternalCoroutinesApi
@kotlinx.coroutines.ExperimentalCoroutinesApi
actual val mainScope = createMainScope()

// TODO: Use background Dispatcher when K/N Coroutines implementation can support it.
// See https://github.com/Kotlin/kotlinx.coroutines/issues/462
@kotlinx.coroutines.InternalCoroutinesApi
@kotlinx.coroutines.ExperimentalCoroutinesApi
actual val backgroundScope = createMainScope()

@InternalCoroutinesApi
@kotlinx.coroutines.ExperimentalCoroutinesApi
private fun createMainScope() = object : CoroutineScope {
    private val dispatcher = MainDispatcher
    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job
}

/**
 * Implementation inspired by:
 * https://github.com/Kotlin/kotlinx.coroutines/issues/462
 */
@InternalCoroutinesApi
@kotlinx.coroutines.ExperimentalCoroutinesApi
private object MainDispatcher: CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatch_get_main_queue()) {
            try {
                block.run()
            } catch (err: Throwable) {
                //logError("UNCAUGHT", err.message ?: "", err)
                throw err
            }
        }
    }

    @kotlinx.coroutines.InternalCoroutinesApi
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, timeMillis * 1_000_000), dispatch_get_main_queue()) {
            try {
                with(continuation) {

                    resumeUndispatched(Unit)
                }
            } catch (err: Throwable) {
                //logError("UNCAUGHT", err.message ?: "", err)
                throw err
            }
        }
    }

    @kotlinx.coroutines.InternalCoroutinesApi
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        val handle = object : DisposableHandle {
            var disposed = false
                private set

            override fun dispose() {
                disposed = true
            }
        }
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, timeMillis * 1_000_000), dispatch_get_main_queue()) {
            try {
                if (!handle.disposed) {
                    block.run()
                }
            } catch (err: Throwable) {
                //logError("UNCAUGHT", err.message ?: "", err)
                throw err
            }
        }

        return handle
    }
}

@ExperimentalUnsignedTypes
actual fun Float.format(precision: Int?): String {
    if (precision == null) {
        return this.toString()
    }

    val formatter = NSNumberFormatter()
    formatter.formatterBehavior = NSNumberFormatterBehaviorDefault
    formatter.numberStyle = NSNumberFormatterNoStyle
    formatter.paddingCharacter = "0"
    formatter.roundingMode = NSNumberFormatterRoundCeiling
    formatter.maximumFractionDigits = precision.toULong()
    formatter.minimumFractionDigits = precision.toULong()
    formatter.setGroupingSeparator(NSLocale.currentLocale.groupingSeparator)
    formatter.setGroupingSize(3)

    return formatter.stringFromNumber(NSNumber(this)) ?: ""
}

actual fun Long.format(): String {
    val formatter = NSNumberFormatter()
    formatter.setFormatterBehavior(NSNumberFormatterDecimalStyle)
    return formatter.stringFromNumber(NSNumber.numberWithLong(this)) ?: ""
}

actual fun Int.format(): String {
    val formatter = NSNumberFormatter()
    formatter.setFormatterBehavior(NSNumberFormatterDecimalStyle)
    return formatter.stringFromNumber(NSNumber.numberWithInt(this)) ?: ""
}
