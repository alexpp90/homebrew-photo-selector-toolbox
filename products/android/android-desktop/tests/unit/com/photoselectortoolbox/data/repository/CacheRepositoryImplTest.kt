package com.photoselectortoolbox.data.repository

import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CacheRepositoryImpl with a mocked ScoreDao.
 */
class CacheRepositoryImplTest {

    private lateinit var dao: ScoreDao
    private lateinit var repo: CacheRepositoryImpl

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repo = CacheRepositoryImpl(dao)
    }

    @Test
    fun `getCachedScore returns entity and updates access time`() = runTest {
        val entity = ScoreEntity("path", 100, 200, sharpnessScore = 42.0)
        coEvery { dao.getScore("path") } returns entity
        coEvery { dao.updateAccessTime(any(), any()) } just Runs

        val result = repo.getCachedScore("path")

        assertEquals(42.0, result!!.sharpnessScore!!, 1e-9)
        coVerify { dao.updateAccessTime("path", any()) }
    }

    @Test
    fun `getCachedScore returns null for missing entry`() = runTest {
        coEvery { dao.getScore("missing") } returns null

        val result = repo.getCachedScore("missing")

        assertNull(result)
        coVerify(exactly = 0) { dao.updateAccessTime(any(), any()) }
    }

    @Test
    fun `cacheScore delegates to DAO insertOrUpdate`() = runTest {
        val entity = ScoreEntity("path", 100, 200, noiseLevel = 3.5)
        coEvery { dao.insertOrUpdate(entity) } just Runs

        repo.cacheScore(entity)

        coVerify { dao.insertOrUpdate(entity) }
    }

    @Test
    fun `clearAll delegates to DAO deleteAll`() = runTest {
        coEvery { dao.deleteAll() } just Runs

        repo.clearAll()

        coVerify { dao.deleteAll() }
    }

    @Test
    fun `pruneToLimit prunes when count exceeds limit`() = runTest {
        coEvery { dao.getCount() } returns 15000
        coEvery { dao.pruneToMru(10000) } just Runs

        repo.pruneToLimit(10000)

        coVerify { dao.pruneToMru(10000) }
    }

    @Test
    fun `pruneToLimit does nothing when count is under limit`() = runTest {
        coEvery { dao.getCount() } returns 500

        repo.pruneToLimit(10000)

        coVerify(exactly = 0) { dao.pruneToMru(any()) }
    }

    @Test
    fun `pruneToLimit uses default limit of 10000`() = runTest {
        coEvery { dao.getCount() } returns 12000
        coEvery { dao.pruneToMru(10000) } just Runs

        repo.pruneToLimit() // uses default

        coVerify { dao.pruneToMru(10000) }
    }
}
