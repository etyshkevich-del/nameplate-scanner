import com.example.nameplateexcel.WorkbookWriter
import java.io.File

fun main() {
    val values = linkedMapOf(
        "C3" to (3 to "IP54"),
        "C4" to (3 to "IE1 — 79,7 %"),
        "C5" to (6 to "2,20 kW"),
        "C6" to (3 to "8,3/4,8 A"),
        "C7" to (3 to ""),
        "C8" to (7 to "2865 r/min"),
        "C9" to (3 to "0,87"),
        "C10" to (3 to "3"),
        "C11" to (7 to "220/380 V Δ/Y"),
    )
    val source = File("app/src/main/assets/template.xlsx")
    val output = File("smoke/generated_from_photo.xlsx")
    output.parentFile.mkdirs()
    output.writeBytes(WorkbookWriter.build(source.inputStream(), values))
    check(output.length() > 5_000)
    println(output.absolutePath)
}
