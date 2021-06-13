package kyklab.humphreysbus

data class AssetsMetadata(
    val assetsVersion: Int,
    val minAppVersion: Int,
    val maxAppVersion: Int,
    val assetsZipMd5: String,
    val files: Array<AssetFile>
) {
    override fun toString(): String {
        var s = """
            assetsVersion: $assetsVersion
            minAppVersion: $minAppVersion
            maxAppVersion: $maxAppVersion
            assetsZipMd5: $assetsZipMd5
        """.trimIndent()
        files.forEach {
            s += "\nname:${it.name} md5:${it.md5}"
        }
        return s
    }

    data class AssetFile(
        val name: String,
        val md5: String
    )
}
