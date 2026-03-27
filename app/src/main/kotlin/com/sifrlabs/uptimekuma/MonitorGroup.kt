package com.sifrlabs.uptimekuma

data class MonitorGroup(
    val name: String,
    val monitors: List<MonitorStatus>
)
