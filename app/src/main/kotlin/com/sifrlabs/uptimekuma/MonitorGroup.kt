package com.sifrlabs.uptimekuma

data class MonitorGroup(
    val name: String,
    val monitors: List<MonitorStatus>,
    val uptimePct: Float = 100f,
    val history: List<Int> = emptyList()
)
