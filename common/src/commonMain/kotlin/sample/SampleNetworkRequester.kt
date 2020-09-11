package sample

import io.ktor.http.HttpMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.serialization.UnstableDefault
import utils.clientEngine
import utils.network.GenericObserver
import utils.network.NetworkEngine
import utils.network.PresentableError

@UnstableDefault
@InternalCoroutinesApi
class SampleNetworkRequester {

    @UnstableDefault
    @InternalCoroutinesApi
    private val networkEngine =
        NetworkEngine(clientEngine)

    @ExperimentalCoroutinesApi
    fun sampleHello() {
        networkEngine.makeRequest(
            HttpMethod.Get,
            "some-endpoint",
            SampleModel.serializer(), object: GenericObserver<SampleModel>() {
                override fun onNext(t: SampleModel) {
                    TODO("Not yet implemented")
                }

                override fun onFail(error: PresentableError) {
                    TODO("Not yet implemented")
                }
            })
    }
}