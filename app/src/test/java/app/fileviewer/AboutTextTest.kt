package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutTextTest {
    @Test
    fun theDetailsHaveTheAppTheSystemAndTheDevice() {
        assertEquals(
            "List 2.4.0 (124, release)\nAndroid 14 (API 34)\nGoogle Pixel 7",
            aboutDetails("List", "2.4.0", 124, false, "14", 34, "Google", "Pixel 7")
        )
    }

    @Test
    fun aDebugBuildIsCalledOne() {
        assertEquals(
            "List 2.4.0 (124, debug)\nAndroid 14 (API 34)\nGoogle Pixel 7",
            aboutDetails("List", "2.4.0", 124, true, "14", 34, "Google", "Pixel 7")
        )
    }

    @Test
    fun aModelThatStartsWithTheMakerIsNotGivenTheNameTwice() {
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)\nSamsung SM-S911B",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, "samsung", "Samsung SM-S911B")
        )
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)\nXiaomi 23049PCD8G",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, "Xiaomi", "Xiaomi 23049PCD8G")
        )
    }

    @Test
    fun aMissingMakerOrModelLeavesNoGaps() {
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)\nPixel 7",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, "", "Pixel 7")
        )
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)\nGoogle",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, "Google", "")
        )
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, "  ", " ")
        )
    }

    @Test
    fun theMakerAndTheModelAreTrimmed() {
        assertEquals(
            "List 2.4.0 (7, release)\nAndroid 13 (API 33)\nGoogle Pixel 7",
            aboutDetails("List", "2.4.0", 7, false, "13", 33, " Google ", " Pixel 7\n")
        )
    }

    @Test
    fun anUnknownVersionIsAQuestionMark() {
        assertEquals(
            "List ? (1, release)\nAndroid 14 (API 34)",
            aboutDetails("List", "", 1, false, "14", 34, "", "")
        )
    }

    @Test
    fun aBigVersionCodeIsWrittenInFull() {
        assertEquals(
            "List 2.4.0 (5000000000, release)\nAndroid 14 (API 34)",
            aboutDetails("List", "2.4.0", 5_000_000_000L, false, "14", 34, "", "")
        )
    }

    @Test
    fun theRepositoryKeyIsNamedWithAShortFingerprint() {
        assertEquals(
            "List 2.5.0 (105, release)\nAndroid 14 (API 34)\nGoogle Pixel 7\nSigned with the repository key (6A:8A:7B:4F:40:FD:3A:C6…)",
            aboutDetails("List", "2.5.0", 105, false, "14", 34, "Google", "Pixel 7", PUBLIC_KEY_SHA256)
        )
    }

    @Test
    fun anotherKeyIsGivenInFull() {
        val own = "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
        assertEquals(
            "List 2.5.0 (105, release)\nAndroid 14 (API 34)\nSigned with another key ($own)",
            aboutDetails("List", "2.5.0", 105, false, "14", 34, "", "", own)
        )
    }

    @Test
    fun anUnreadableKeyIsLeftOut() {
        assertEquals(
            "List 2.5.0 (105, release)\nAndroid 14 (API 34)",
            aboutDetails("List", "2.5.0", 105, false, "14", 34, "", "", "")
        )
    }
}
