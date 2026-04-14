package zone.clanker.opsx.model

data class OpsxConfig(
    val outputDir: String,
    val specsDir: String,
    val changesDir: String,
    val projectFile: String,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
