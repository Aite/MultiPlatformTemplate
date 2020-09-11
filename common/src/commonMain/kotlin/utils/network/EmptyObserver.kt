package utils.network

class EmptyObserver<T>: GenericObserver<T>() {
    override fun onNext(t: T) {
        // Do Nothing
    }

    override fun onFail(error: PresentableError) {
        // Do Nothing
    }
}