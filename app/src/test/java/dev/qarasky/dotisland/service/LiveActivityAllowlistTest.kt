package dev.qarasky.dotisland.service

import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.util.PersonalActivityPolicy as Policy
import org.junit.Assert.*
import org.junit.Test

class LiveActivityAllowlistTest {
    @Test fun onlySpotifyMediaIsAdmitted() {
        assertEquals(IslandMode.Music, Policy.mode(Policy.SPOTIFY, null, true, true, "Song", "Artist"))
        assertEquals(IslandMode.Empty, Policy.mode("com.soundcloud.android", null, true, true, "Song", "Artist"))
        assertEquals(IslandMode.Empty, Policy.mode(Policy.SPOTIFY, null, false, false, "Promo", "Listen now"))
    }
    @Test fun onlyNativeActualCallsAreAdmitted() {
        assertEquals(IslandMode.IncomingCall, Policy.mode(Policy.DIALER, "call", false, true, "Caller", "Ringing"))
        assertEquals(IslandMode.IncomingCall, Policy.mode(Policy.TELECOM, "call", false, true, "Caller", "Active"))
        assertEquals(IslandMode.Empty, Policy.mode(Policy.DIALER, "msg", false, false, "Voicemail", "New message"))
        assertEquals(IslandMode.Empty, Policy.mode("com.whatsapp", "call", false, true, "Caller", "Ringing"))
    }
    @Test fun dodoUsesActualInstalledPackageAndRejectsPromotions() {
        assertEquals(IslandMode.LiveActivity, Policy.mode("ru.dodopizza.app", null, false, false, "Ваш заказ", "Готовим пиццу"))
        assertEquals(IslandMode.Empty, Policy.mode("com.dodopizza.app", null, false, true, "Your order", "Preparing"))
        assertEquals(IslandMode.Empty, Policy.mode(Policy.DODO, null, false, false, "Скидка", "Закажите пиццу"))
    }
    @Test fun unrelatedNotificationsAndEveryGroupSummaryAreIgnored() {
        assertEquals(IslandMode.Empty, Policy.mode("org.telegram.messenger", "msg", false, false, "Message", "Text"))
        assertEquals(IslandMode.Empty, Policy.mode(Policy.DODO, null, false, true, "Your order", "Preparing", groupSummary = true))
    }
}
