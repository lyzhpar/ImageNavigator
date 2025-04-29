package com.example.imagenavigator.model

data class Adventure(
    val adventureTitle: String,
    val folderUri: String,
    val images: List<ImageData>
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
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)