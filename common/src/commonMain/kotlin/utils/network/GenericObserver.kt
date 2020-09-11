package utils.network

abstract class GenericObserver<T> {
    abstract fun onNext(t: T)
    abstract fun onFail(error: PresentableError)
}


