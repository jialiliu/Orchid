package com.eden.orchid.kotlindoc.helpers

import com.copperleaf.dokka.json.models.KotlinClassDoc
import com.copperleaf.dokka.json.models.KotlinPackageDoc
import com.eden.common.util.EdenUtils
import com.eden.orchid.api.OrchidContext
import com.eden.orchid.kotlindoc.model.KotlinRootdoc
import com.eden.orchid.utilities.InputStreamIgnorer
import com.eden.orchid.utilities.OrchidUtils
import com.google.inject.name.Named
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class KotlindocInvokerImpl
@Inject
constructor(
        @Named("src") val resourcesDir: String,
        val client: OkHttpClient,
        val context: OrchidContext) : KotlindocInvoker {

    val repos = listOf(
            "https://jcenter.bintray.com",
            "https://kotlin.bintray.com/kotlinx",
            "https://jitpack.io"
    )

    override fun getRootDoc(sourceDirs: List<String>): KotlinRootdoc? {
        val dokkaOutputPath = OrchidUtils.getTempDir("dokka", true)
        val dokkaJarPaths = getMavenJars("com.github.copper-leaf.dokka-json:dokka-json:0.1.9")

        executeDokka(dokkaJarPaths, dokkaOutputPath, sourceDirs)

        return getKotlinRootdoc(dokkaOutputPath)
    }

// Download jars from Maven
//----------------------------------------------------------------------------------------------------------------------

    private fun getMavenJars(vararg target: String): List<String> {
        val processedTargets = HashSet<String>()
        val targetsToProcess = ArrayDeque<String>()
        targetsToProcess.addAll(target)

        val resolvedJars = ArrayList<String>()

        while (targetsToProcess.peek() != null) {
            val currentTarget = targetsToProcess.pop()

            // we've already processed this artifact, skip this iteration
            if (processedTargets.contains(currentTarget)) continue
            processedTargets.add(currentTarget)

            // otherwise resolve its dependencies and download this jar
            val groupId = currentTarget.split(":").getOrElse(0) { "" }
            val artifactId = currentTarget.split(":").getOrElse(1) { "" }
            val version = currentTarget.split(":").getOrElse(2) { "" }

            targetsToProcess.addAll(getTransitiveDependencies(groupId, artifactId, version))
            val downloadedJarPath = downloadJar(groupId, artifactId, version)
            if (!EdenUtils.isEmpty(downloadedJarPath)) {
                resolvedJars.add(downloadedJarPath)
            }
        }

        return resolvedJars
    }

    private fun getTransitiveDependencies(groupId: String, artifactId: String, version: String): List<String> {
        for (repo in repos) {
            val pomUrl = "$repo/${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version.pom"
            client.newCall(Request.Builder().url(pomUrl).build()).execute().use {
                if (it.isSuccessful) {
                    val mavenMetadataXml = it.body()?.string() ?: ""

                    val doc = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(InputSource(StringReader(mavenMetadataXml)))

                    val itemsTypeT1 = XPathFactory
                            .newInstance()
                            .newXPath()
                            .evaluate("/project/dependencies/dependency", doc, XPathConstants.NODESET) as NodeList

                    val transitiveDependencies = ArrayList<String>()

                    for (i in 0 until itemsTypeT1.length) {
                        var childGroupId = ""
                        var childArtifactId = ""
                        var childVersion = ""
                        var scope = ""
                        for (j in 0 until itemsTypeT1.item(i).childNodes.length) {
                            val name = itemsTypeT1.item(i).childNodes.item(j).nodeName
                            val value = itemsTypeT1.item(i).childNodes.item(j).textContent
                            when (name) {
                                "groupId"    -> childGroupId = value
                                "artifactId" -> childArtifactId = value
                                "version"    -> childVersion = value
                                "scope"      -> scope = value
                            }
                        }

                        if (scope == "compile") {
                            transitiveDependencies.add("$childGroupId:$childArtifactId:$childVersion")
                        }
                    }

                    return transitiveDependencies
                }
            }
        }

        return emptyList()
    }

    private fun downloadJar(groupId: String, artifactId: String, version: String): String {
        for (repo in repos) {
            val downloadedFile = File(OrchidUtils.getCacheDir("kotlindoc").toFile(), "$artifactId-$version.jar")

            if (downloadedFile.exists()) {
                return downloadedFile.absolutePath
            }
            else {
                val jarUrl = "$repo/${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version.jar"
                client.newCall(Request.Builder().url(jarUrl).build()).execute().use {
                    if (it.isSuccessful) {
                        val sink = Okio.buffer(Okio.sink(downloadedFile))
                        sink.writeAll(it.body()!!.source())
                        sink.close()

                        return downloadedFile.absolutePath
                    }
                }
            }
        }

        return ""
    }

// Run Dokka
//----------------------------------------------------------------------------------------------------------------------

    private fun executeDokka(dokkaJarPath: List<String>, dokkaOutputPath: Path, sourceDirs: List<String>) {
        val process = ProcessBuilder()
                .command(
                        "java",
                        "-classpath", dokkaJarPath.joinToString(File.pathSeparator), // classpath of downloaded jars
                        "org.jetbrains.dokka.MainKt", // Dokka main class
                        "-format", "json", // JSON format (so Orchid can pick it up afterwards)
                        "-noStdlibLink",
                        "-impliedPlatforms", "JVM",
                        "-src", sourceDirs.joinToString(separator = File.pathSeparator), // the sources to process
                        "-output", dokkaOutputPath.toFile().absolutePath // where Orchid will find them later
                )
                .directory(File(resourcesDir))
                .start()

        Executors.newSingleThreadExecutor().submit(InputStreamIgnorer(process.inputStream))
        process.waitFor()
    }

// Process Dokka output to a model Orchid can use
//----------------------------------------------------------------------------------------------------------------------

    private fun getKotlinRootdoc(dokkaOutputPath: Path): KotlinRootdoc {
        val packages = getDokkaPackagePages(dokkaOutputPath)
        val classes = getDokkaClassPages(dokkaOutputPath)

        return KotlinRootdoc(
                packages,
                classes
        )
    }

    private fun getDokkaPackagePages(dokkaOutputPath: Path): List<KotlinPackageDoc> {
        val packagePagesList = ArrayList<KotlinPackageDoc>()
        dokkaOutputPath
                .toFile()
                .walkTopDown()
                .filter { it.isFile && it.name == "index.json" }
                .map { KotlinPackageDoc.fromJson(it.readText()) }
                .toCollection(packagePagesList)

        return packagePagesList
    }

    private fun getDokkaClassPages(dokkaOutputPath: Path): List<KotlinClassDoc> {
        val classPagesList = ArrayList<KotlinClassDoc>()
        dokkaOutputPath
                .toFile()
                .walkTopDown()
                .filter { it.isFile && it.name != "index.json" }
                .map { KotlinClassDoc.fromJson(it.readText()) }
                .toCollection(classPagesList)

        return classPagesList
    }

//    private fun getPackagedoc(packageName: String, packages: List<KotlinPackageDoc>): KotlinPackageDoc {
//        return packages.find { it.qualifiedName == packageName } ?: throw IllegalArgumentException("Error: Class was defined in package that does not exist: package=$packageName")
//    }

}