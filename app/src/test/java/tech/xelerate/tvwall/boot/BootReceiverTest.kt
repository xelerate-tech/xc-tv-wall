package tech.xelerate.tvwall.boot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test fun launchesAfterBootAndAfterSelfUpdate() {
        assertTrue(BootReceiver.shouldLaunch("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.shouldLaunch("android.intent.action.MY_PACKAGE_REPLACED"))
        assertFalse(BootReceiver.shouldLaunch("android.intent.action.SCREEN_ON"))
        assertFalse(BootReceiver.shouldLaunch(null))
    }
}
