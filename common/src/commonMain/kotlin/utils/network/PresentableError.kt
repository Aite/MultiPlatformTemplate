package utils.network

data class PresentableError(
    val title: String,
    val message: String,
    val code: Int?) {
    constructor() :this(
            "Hata",
            "Beklenmedik bir hata oluştu ve ekibimiz bilgilendirildi. Lütfen daha sonra tekrar deneyin ya da Volt Lines yetkilileriyle iletişime geçin.",
            null
            )

    constructor(message: String, code: Int? = null) :this("Hata", message, code)
}