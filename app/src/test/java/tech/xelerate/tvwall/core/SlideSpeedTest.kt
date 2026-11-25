package tech.xelerate.tvwall.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideSpeedTest {
    @Test fun choicesAreAscendingAndIncludeDefault() {
        assertEquals(listOf(3, 5, 8, 10, 15, 20, 30, 45, 60), SlideSpeed.CHOICES_SECONDS)
        assertEquals(10, SlideSpeed.DEFAULT_SECONDS)
    }

    @Test fun fasterAndSlowerStepThroughChoicesAndStopAtEnds() {
        assertEquals(8, SlideSpeed.faster(10))
        assertEquals(15, SlideSpeed.slower(10))
        assertEquals(3, SlideSpeed.faster(3))
        assertEquals(60, SlideSpeed.slower(60))
    }

    @Test fun unknownStoredValueSnapsToNearestChoice() {
        assertEquals(10, SlideSpeed.normalize(11))
        assertEquals(3, SlideSpeed.normalize(-4))
        assertEquals(60, SlideSpeed.normalize(500))
        assertEquals(8, SlideSpeed.faster(11))
        assertEquals(15, SlideSpeed.slower(11))
    }
}
