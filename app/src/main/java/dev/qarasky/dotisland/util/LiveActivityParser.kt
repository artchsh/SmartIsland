/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.util

/** Conservative Dodo notification parsing; never invents delivery percentage/ETA. */
object LiveActivityParser {
    private val etaPattern = Regex("(?<![\\p{L}\\d])\\d{1,3}(?:\\s*[–-]\\s*\\d{1,3})?\\s*(?:minutes?|mins?|мин(?:ут[аы]?)?)(?!\\p{L})", RegexOption.IGNORE_CASE)
    private val promotions = listOf("discount", "coupon", "promo", "% off", "order now", "скидк", "промокод", "акци", "закажите")
    private val orderStates = listOf("order accepted", "order confirmed", "your order", "preparing", "baking", "out for delivery",
        "courier", "delivered", "заказ принят", "заказ подтвержд", "ваш заказ", "готовим", "готовится", "печи", "курьер", "доставлен")
    fun isOrderTracking(title: String, text: String, ongoing: Boolean, progressMax: Int): Boolean {
        val content = "$title $text".lowercase()
        if (promotions.any(content::contains)) return false
        return orderStates.any(content::contains) || (ongoing && progressMax > 0)
    }
    fun eta(title: String, text: String): String? =
        etaPattern.find("$title $text")?.value
    fun progress(value: Int, max: Int): Float? = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else null
}
