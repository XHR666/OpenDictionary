package io.github.xhr666.opendictionary.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val valid = """
        {"schema":1,"packageName":"io.github.xhr666.opendictionary","versionCode":3,
         "versionName":"0.1.2-pre","minSdk":33,"apkAsset":"app.apk",
         "sha256":"${"a".repeat(64)}","size":100,"publishedAt":"2026-01-01T00:00:00Z","notes":"x"}
    """.trimIndent()

    @Test fun parsesValidManifest() {
        val m = UpdateManifest.parse(valid)!!
        assertEquals(3, m.versionCode)
        assertEquals("app.apk", m.apkAsset)
    }

    @Test fun newerVersionAccepted() {
        assertTrue(UpdateManifest.parse(valid)!!.isNewerThan(currentVersionCode = 2, currentMinSdk = 33))
    }

    @Test fun sameVersionRejected() {
        assertFalse(UpdateManifest.parse(valid)!!.isNewerThan(currentVersionCode = 3, currentMinSdk = 33))
    }

    @Test fun tooHighMinSdkRejected() {
        assertFalse(UpdateManifest.parse(valid)!!.isNewerThan(currentVersionCode = 2, currentMinSdk = 30))
    }

    @Test fun shortShaRejected() {
        val bad = valid.replace("a".repeat(64), "abc")
        assertFalse(UpdateManifest.parse(bad)!!.isNewerThan(2, 33))
    }

    @Test fun invalidJsonReturnsNull() {
        assertNull(UpdateManifest.parse("not json"))
    }
}
