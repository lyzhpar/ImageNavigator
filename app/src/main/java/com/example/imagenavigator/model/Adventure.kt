package com.example.imagenavigator.model




data class Adventure(
    var adventureTitle: String,
    val folderUri: String,
    val images: List<ImageData>,
    val startImagePath: String? = null
)

data class ImageData(
    val imageName: String,
    val zones: List<ZoneData>
)

data class ZoneData(
    val linkedImagePath: String?,
    val rect: RectData
)

data class RectData(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float
)