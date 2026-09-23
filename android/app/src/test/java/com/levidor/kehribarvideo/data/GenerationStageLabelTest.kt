package com.levidor.kehribarvideo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GenerationStageLabelTest {

    @Test
    fun unknownDurationStageDoesNotInventPercentage() {
        val label = generationStageLabel("quantizing", null)

        assertEquals("Model INT8 için optimize ediliyor…", label)
        assertFalse(label.contains("%"))
    }

    @Test
    fun measuredGenerationProgressIsShown() {
        assertEquals(
            "Video oluşturuluyor… %37",
            generationStageLabel("generating", 37)
        )
    }

    @Test
    fun progressIsClampedToApiRange() {
        assertEquals("Video oluşturuluyor… %100", generationStageLabel("generating", 140))
    }
}
