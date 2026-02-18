package com.audioslice.pro.data.model

sealed class SplitMethod {
    abstract val displayName: String
    abstract val unit: String
    abstract val minValue: Float
    abstract val maxValue: Float
    abstract val defaultValue: Float
    
    data class BySize(
        override val displayName: String = "By File Size",
        override val unit: String = "MB",
        override val minValue: Float = 10f,
        override val maxValue: Float = 500f,
        override val defaultValue: Float = 100f
    ) : SplitMethod()
    
    data class ByDuration(
        override val displayName: String = "By Duration",
        override val unit: String = "min",
        override val minValue: Float = 1f,
        override val maxValue: Float = 60f,
        override val defaultValue: Float = 10f
    ) : SplitMethod()
}
