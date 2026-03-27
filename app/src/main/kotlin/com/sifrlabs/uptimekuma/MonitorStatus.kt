package com.sifrlabs.uptimekuma

data class MonitorStatus(
    val id: Int,
    val name: String,
    val status: Int,
    val uptimePct: Float = 100f,
    val history: List<Int> = emptyList()
) {
    companion object {
        const val STATUS_DOWN = 0
        const val STATUS_UP = 1
        const val STATUS_PENDING = 2
        const val STATUS_MAINTENANCE = 3
    }
}
