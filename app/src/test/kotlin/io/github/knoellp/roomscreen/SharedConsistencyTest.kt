package io.github.knoellp.roomscreen

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `shared/design/tokens.json` against [Tokens] and `shared/strings/{en,de}.json` against the Android string resources,
 * in both directions: a name or value that exists on one side only, or differs, fails the test with the list of differences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedConsistencyTest {
    private val shared = File(System.getProperty("shared.dir") ?: "../shared")
    private fun json(path: String): JsonObject = Json.parseToJsonElement(File(shared, path).readText(Charsets.UTF_8)).jsonObject

    @Test fun tokensMatchTheJsonInBothDirections() {
        val doc = json("design/tokens.json")
        val groups = doc.keys.filter { it != "description" }.toSet()
        val diffs = ArrayList<String>()
        for (g in groups union Tokens.raw.keys) {
            val fromJson = doc[g]?.jsonObject
            val fromCode = Tokens.raw[g]
            if (fromJson == null) { diffs += "group $g: only in Tokens"; continue }
            if (fromCode == null) { diffs += "group $g: only in tokens.json"; continue }
            for (name in fromJson.keys union fromCode.keys) {
                val j = (fromJson[name] as? JsonPrimitive)
                val c = fromCode[name]
                when {
                    j == null -> diffs += "$g.$name: only in Tokens ($c)"
                    c == null -> diffs += "$g.$name: only in tokens.json (${j.contentOrNull})"
                    c is String -> if (!c.equals(j.contentOrNull, ignoreCase = false)) diffs += "$g.$name: Tokens $c, tokens.json ${j.contentOrNull}"
                    else -> if ((c as Number).toDouble() != j.doubleOrNull) diffs += "$g.$name: Tokens $c, tokens.json ${j.contentOrNull}"
                }
            }
        }
        assertTrue(diffs.isEmpty(), "tokens differ:\n" + diffs.joinToString("\n"))
    }

    private fun xmlNames(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }

    private fun resourcesFor(locale: Locale): Resources {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val conf = Configuration(ctx.resources.configuration).apply { setLocale(locale) }
        return ctx.createConfigurationContext(conf).resources
    }

    private fun check(file: String, xml: String, locale: Locale) {
        val want = json(file).mapValues { (it.value as JsonPrimitive).content }
        val diffs = ArrayList<String>()
        val xmlNames = xmlNames(xml)
        for (n in want.keys - xmlNames) diffs += "$n: only in $file"
        for (n in xmlNames - want.keys) diffs += "$n: only in $xml"
        val res = resourcesFor(locale)
        for ((name, value) in want) {
            val id = res.getIdentifier(name, "string", "io.github.knoellp.roomscreen")
            if (id == 0) { diffs += "$name: no Android resource"; continue }
            val actual = res.getString(id)
            if (actual != value) diffs += "$name: resource \"$actual\", $file \"$value\""
        }
        assertTrue(diffs.isEmpty(), "$file differs from $xml:\n" + diffs.joinToString("\n"))
    }

    @Test fun englishStringsMatch() = check("strings/en.json", "src/main/res/values/strings.xml", Locale.ENGLISH)

    @Test fun germanStringsMatch() = check("strings/de.json", "src/main/res/values-de/strings.xml", Locale.GERMAN)

    @Test fun bothLanguagesHaveTheSameNames() {
        assertEquals(json("strings/en.json").keys, json("strings/de.json").keys)
    }
}
