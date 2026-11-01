package com.example.network

/**
 * Works out the order in which the keys of a pool are attempted.
 *
 * Rotation starts at the key that worked last time, so a dead key at the top of the
 * list is only contacted once instead of on every request.
 */
object ApiKeyRotation {
    fun order(poolSize: Int, activeIndex: Int): List<Int> {
        if (poolSize <= 0) return emptyList()
        val start = ((activeIndex % poolSize) + poolSize) % poolSize
        return List(poolSize) { offset -> (start + offset) % poolSize }
    }
}
