package utils.network

import utils.backgroundScope
import utils.mainScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.content
import kotlinx.serialization.json.intOrNull

val vlJsonConfiguration = JsonConfiguration.Stable.copy(
    ignoreUnknownKeys = true,
    isLenient = true,
    serializeSpecialFloatingPointValues = true
)

@UnstableDefault
@kotlinx.coroutines.InternalCoroutinesApi
open class NetworkEngine(
    clientEngine: HttpClientEngine = utils.clientEngine
) {
    private val client = HttpClient(clientEngine){
        expectSuccess = false
        install(JsonFeature){
            serializer = KotlinxSerializer(Json(vlJsonConfiguration))
        }
    }

    companion object {
        const val ENABLE_LOGGING = true
        val baseUrlPath = "demo.voltlines.com/case-study/5"
    }

    @UnstableDefault
    private val json = Json(vlJsonConfiguration)

    /**
     * for the requests you need the response and have a request object
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun <T, R> makeRequest(
        requestMethod: HttpMethod,
        requestPath: String,
        requestModel: SerializationStrategy<R>,
        requestObject: R,
        responseModel: DeserializationStrategy<T>,
        observer: GenericObserver<T>
    ) {
        backgroundScope.launch {
            request(requestMethod, requestPath, requestObject, requestModel, observer) { response ->
                parseResponse(response, responseModel, observer)
            }
        }
    }

    /**
     * for the requests that you need response and do not have a request object
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun <T> makeRequest(requestMethod: HttpMethod,
                                requestPath: String,
                                responseModel: DeserializationStrategy<T>,
                                observer: GenericObserver<T>){
        backgroundScope.launch {
            request(requestMethod, requestPath, "", null, observer) { response ->
                parseResponse(response, responseModel, observer)
            }
        }
    }

    /**
     * for the requests that you do not care about response and have a request object
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun <T> makeRequest(
        requestMethod: HttpMethod,
        requestPath: String,
        requestModel: SerializationStrategy<T>,
        requestObject: T,
        observer: GenericObserver<Boolean>?
    ) {
        backgroundScope.launch {
            request(requestMethod, requestPath, requestObject, requestModel, observer) { response ->
                observer?.let {
                    parseResponse(response, it)
                }
            }
        }
    }

    /**
     * for the requests that you do not care about response and do not have a request object
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun makeRequest(requestMethod: HttpMethod,
                        requestPath: String,
                        observer: GenericObserver<Boolean>?) {
        backgroundScope.launch {
            request(requestMethod, requestPath, null, null, observer) { response ->
                observer?.let {
                    parseResponse(response, it)
                } ?: run {
                    parseResponse(response, EmptyObserver())
                }
            }
        }
    }

    // requestModel need to be sent for Get http methods to enable requestObject parsing to query string
    @ExperimentalCoroutinesApi
    private suspend fun <T, R> request(requestMethod: HttpMethod,
                                       requestPath: String,
                                       requestObject: T? = null,
                                       requestModel: SerializationStrategy<T>? = null,
                                       errorHandlingObserver: GenericObserver<R>?,
                                       onFinish: suspend ((response: HttpResponse) -> (Unit))
    ) {

        var requestEncodedPath = requestPath
        if (requestMethod == HttpMethod.Get) {
            // Parse requestObject as query string
            val queryString = parseQueryString<T>(requestModel, requestObject)
            if (queryString.isNotBlank()) {
                requestEncodedPath += queryString
            }
        }
        val requestHeaders = mutableMapOf<String, String>()
        if (requestMethod != HttpMethod.Get) {
            requestHeaders.put("Content-Type", "application/json; charset=utf-8")
        }

        var requestBody: Any? = ""
        if (requestMethod != HttpMethod.Get) {
            requestModel?.let { bodySerializer ->
                if ((requestObject is Map<*, *>) ||
                    (requestObject is List<*>)
                ) {
                    requestBody = json.toJson(
                        bodySerializer,
                        requestObject
                    )
                } else {
                    requestBody = requestObject
                }
            } ?: run {
                requestBody = requestObject
            }

        }

        if (ENABLE_LOGGING) {
            println("[NetworkManager] request host: ${baseUrlPath}")
            println("[NetworkManager] request method: $requestMethod")
            println("[NetworkManager] request path: $requestEncodedPath")
            println("[NetworkManager] request headers: $requestHeaders")
            println("[NetworkManager] request body object: $requestObject")
            if ( (requestModel != null) && (requestObject != null) ) {
                @Suppress("NAME_SHADOWING")
                println(
                    "[NetworkManager] request body object: ${json.stringify(
                        requestModel,
                        requestObject
                    )}"
                )
            }
        }

        try {
            val httpStatement = client.request<HttpStatement> {
                url {
                    protocol = URLProtocol.HTTPS
                    method = requestMethod
                    host = baseUrlPath
                    encodedPath = requestEncodedPath
                    requestBody?.let { body = it }
                    headers {
                        requestHeaders.forEach { headerEntry ->
                            header(headerEntry.key, headerEntry.value)
                        }
                    }
                }
            }

            httpStatement.execute { response ->
                onFinish(response)
            }
        } catch (e: Throwable) {
            errorHandlingObserver?.let {
                handleException(e, it)
            }
        }
}

    private fun <T> parseQueryString(
        requestModel: SerializationStrategy<T>?,
        requestObject: T?
    ): String {
        var queryString = ""
        @Suppress("NAME_SHADOWING")
        requestObject?.let { requestObject ->
            requestModel?.let { requestModel ->
                val jsonElement = json.toJson(requestModel, requestObject)
                for (entry in jsonElement.jsonObject.content.entries) {
                    val prefix = if (queryString.isBlank()) "?" else "&"
                    queryString += prefix +
                            entry.key.encodeURLQueryComponent() +
                            "=" +
                            entry.value.content.encodeURLQueryComponent()
                }
            }
        }
        return queryString
    }

    @InternalCoroutinesApi
    @ExperimentalCoroutinesApi
    private suspend fun <T> parseResponse(response: HttpResponse, responseModel: DeserializationStrategy<T>?, observer: GenericObserver<T>) {
        if (ENABLE_LOGGING) {
            println("[NetworkManager] response status: ${response.status.value}")
        }
        try {
            if (response.status.isSuccess()) {
                responseModel?.let {
                    val responseBody = response.readText(Charsets.UTF_8)
                    if (ENABLE_LOGGING) {
                        println("[NetworkManager] response body: $responseBody")
                    }
                    val parsedJson = json.parse(it, responseBody)
                    mainScope.launch {
                        observer.onNext(parsedJson)
                    }
                } ?: run {
                    if (ENABLE_LOGGING) {
                        println("[NetworkManager] responseModel was null")
                    }
                    mainScope.launch {
                        observer.onFail(PresentableError())
                    }
                }

            } else {
                handleFailResponse(response, observer)
            }
        } catch (exception: Throwable) {
            handleException(exception, observer)
        }
    }

    @InternalCoroutinesApi
    @ExperimentalCoroutinesApi
    private suspend fun parseResponse(response: HttpResponse, observer: GenericObserver<Boolean>) {
        if (ENABLE_LOGGING) {
            println("[NetworkManager] request url: ${response.request.url}")
            println("[NetworkManager] response status: ${response.status.value}")
            println("[NetworkManager] response headers: ${response.headers}")
        }
        try {
            if (response.status.isSuccess()) {
                val responseBody = response.readText(Charsets.UTF_8)
                if (ENABLE_LOGGING) {
                    println("[NetworkManager] response body: $responseBody")
                }
                mainScope.launch {
                    observer.onNext(true)
                }
            }
            else {
                handleFailResponse(response, observer)
            }
        } catch (exception: Throwable) {
            handleException(exception, observer)
        }
    }

    @ExperimentalCoroutinesApi
    private suspend fun <T> handleException(e: Throwable, observer: GenericObserver<T>) {
        var errorMessage: String? = null

        if (e is ResponseException ||
                e is RedirectResponseException ||
                e is ServerResponseException) {
            if (ENABLE_LOGGING) {
                println("[NetworkManager] response exception: $e")
            }
            e.message?.let {
                errorMessage = it
            }
            mainScope.launch {
                errorMessage?.let {
                    val networkError =
                        PresentableError("", it, null)
                    observer.onFail(networkError)
                } ?: run {
                    observer.onFail(PresentableError())
                }
            }
        } else if (e is ClientRequestException) {
            if (ENABLE_LOGGING) {
                println("[NetworkManager] client request exception: $e")
            }
            handleFailResponse(e.response, observer)
        } else {
            if (ENABLE_LOGGING) {
                println("[NetworkManager] unknown exception: $e")
            }
            mainScope.launch {
                observer.onFail(PresentableError())
            }
        }
    }

    @ExperimentalCoroutinesApi
    private suspend fun <T> handleFailResponse(response: HttpResponse, observer: GenericObserver<T>) {
        var errorMessage: String? = null
        var errorCode: Int? = null

        try {
            val responseString = response.readText()
            if (ENABLE_LOGGING) {
                println("[NetworkManager] failed response body: $responseString")
            }

            val jObj = json.parseJson(responseString)
            errorMessage = jObj.jsonObject["message"]?.toString()
            errorCode = jObj.jsonObject["error_code"]?.intOrNull

        } catch (e1: Throwable) {
            if (ENABLE_LOGGING) {
                println("[NetworkManager] error: ${e1.message}")
            }
        }
        mainScope.launch {
            errorMessage?.let {
                if (ENABLE_LOGGING) {
                    println("[NetworkManager] errorMessage: $it")
                }
                val networkError =
                    PresentableError(it, errorCode)
                observer.onFail(networkError)
            } ?: run {
                observer.onFail(PresentableError())
            }
        }
    }
}