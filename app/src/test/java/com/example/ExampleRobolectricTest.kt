package com.example

import com.example.data.repository.CreatorRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleAppTest {

    @Test
    fun testCreatorsDataset() {
        val videos = CreatorRepository.sampleVideos
        assertTrue(videos.isNotEmpty())
        assertEquals("ju.pasqualotto", videos[0].nickname)
        assertEquals("fitwithjoao", videos[1].nickname)
        assertEquals("dancequeen_official", videos[2].nickname)
        assertEquals("lifestyle.ana", videos[3].nickname)
    }
}
