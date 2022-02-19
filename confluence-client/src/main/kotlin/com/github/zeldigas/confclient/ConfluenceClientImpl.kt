package com.github.zeldigas.confclient

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.zeldigas.confclient.model.Attachment
import com.github.zeldigas.confclient.model.ConfluencePage
import com.github.zeldigas.confclient.model.PageAttachments
import com.github.zeldigas.confclient.model.Space
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.io.path.fileSize

class ConfluenceClientImpl(
    private val apiBase: String,
    private val httpClient: HttpClient
) : ConfluenceClient {

    companion object {
        private const val PAGE_SIZE = 100
    }

    override suspend fun describeSpace(key: String, expansions: List<String>): Space {
        return httpClient.get("$apiBase/space/$key") {
            addExpansions(expansions)
        }
    }

    override suspend fun getPage(
        space: String,
        title: String,
        status: List<String>?,
        expansions: List<String>
    ): ConfluencePage {
        val results = findPages(space, title, status, expansions)

        return extractSinglePage(results)
    }

    override suspend fun getPageOrNull(
        space: String,
        title: String,
        status: List<String>?,
        expansions: List<String>
    ): ConfluencePage? {
        val results = findPages(space, title, status, expansions)

        return if (results.isEmpty()) {
            null
        } else {
            extractSinglePage(results)
        }
    }

    private fun extractSinglePage(results: List<ConfluencePage>): ConfluencePage {
        if (results.isEmpty()) {
            throw PageNotFoundException()
        } else if (results.size > 1) {
            throw TooManyPagesFound(results)
        } else {
            return results.first()
        }
    }

    override suspend fun findPages(
        space: String?,
        title: String,
        status: List<String>?,
        expansions: List<String>
    ): List<ConfluencePage> {
        val result: PageSearchResult = httpClient.get("$apiBase/content") {
            space?.let { parameter("spaceKey", it) }
            parameter("title", title)
            status?.let { parameter("status", it.toString()) }
            addExpansions(expansions)
        }
        return result.results
    }

    private fun HttpRequestBuilder.addExpansions(expansions: List<String>) {
        if (expansions.isNotEmpty()) {
            parameter("expand", expansions.joinToString(","))
        }
    }

    override suspend fun createPage(value: PageContentInput, updateParameters: PageUpdateOptions, expansions: List<String>?): ConfluencePage {
        if (value.space.isNullOrEmpty()) {
            throw IllegalArgumentException("Space is required when creating pages")
        }
        return httpClient.post("$apiBase/content") {
            if (expansions != null){
                addExpansions(expansions)
            }
            contentType(ContentType.Application.Json)
            body = toPageData(value, updateParameters)
        }
    }

    override suspend fun updatePage(
        pageId: String,
        value: PageContentInput,
        updateParameters: PageUpdateOptions
    ): ConfluencePage {
        return httpClient.put("$apiBase/content/$pageId") {
            contentType(ContentType.Application.Json)
            body = toPageData(value, updateParameters)
        }
    }

    private fun toPageData(
        value: PageContentInput,
        pageUpdateOptions: PageUpdateOptions
    ): Map<String, Any?> {
        return buildMap {
            put("type", "page")
            value.parentPage?.let { put("ancestors", listOf(mapOf("id" to it))) }
            put("title", value.title)
            put(
                "body", mapOf(
                    "storage" to mapOf(
                        "value" to value.content,
                        "representation" to "storage"
                    )
                )
            )
            put("version", buildMap<String, Any> {
                put("number", value.version)
                put("minorEdit", !pageUpdateOptions.notifyWatchers)
                pageUpdateOptions.message?.let { put("message", it) }
            })
            if (value.space != null) {
                put("space", mapOf("key" to value.space))
            }
        }
    }

    override suspend fun setPageProperty(pageId: String, name: String, value: PagePropertyInput) {
        return httpClient.put("$apiBase/content/$pageId/property/$name") {
            contentType(ContentType.Application.Json)
            body = value
        }
    }

    override suspend fun findChildPages(pageId: String, expansions: List<String>?): List<ConfluencePage> {
        val result = mutableListOf<ConfluencePage>()
        var start = 0
        var limit = PAGE_SIZE
        var completed = false
        do {
            val page = httpClient.get<PageSearchResult>("$apiBase/content/$pageId/child/page") {
                addExpansions(expansions ?: emptyList())
                parameter("start", start)
                parameter("limit", limit)
            }
            result.addAll(page.results)
            limit = page.limit
            start += limit
            completed = page.size != page.limit
        } while (!completed)
        return result
    }

    override suspend fun deletePage(pageId: String) {
        httpClient.delete<Unit>("$apiBase/content/$pageId")
    }

    override suspend fun deleteLabel(pageId: String, label: String) {
        httpClient.delete<Unit>("$apiBase/content/$pageId/label/$label")
    }

    override suspend fun addLabels(pageId: String, labels: List<String>) {
        httpClient.post<Unit>("$apiBase/content/$pageId/label") {
            contentType(ContentType.Application.Json)
            body = labels.map { mapOf("name" to it) }
        }
    }

    override suspend fun addAttachments(
        pageId: String,
        pageAttachmentInput: List<PageAttachmentInput>
    ): PageAttachments {
        return httpClient.submitFormWithBinaryData("$apiBase/content/$pageId/child/attachment", formData {
            for (attachment in pageAttachmentInput) {
                addAttachmentToForm(attachment)
            }
        }) {
            header("X-Atlassian-Token", "nocheck")
            header("Accept", "application/json")
        }
    }

    override suspend fun updateAttachment(
        pageId: String,
        attachmentId: String,
        pageAttachmentInput: PageAttachmentInput
    ): Attachment {
        return httpClient.submitFormWithBinaryData(
            "$apiBase/content/$pageId/child/attachment/$attachmentId/data",
            formData {
                addAttachmentToForm(pageAttachmentInput)
            }) {
            header("X-Atlassian-Token", "nocheck")
            header("Accept", "application/json")
        }
    }

    override suspend fun deleteAttachment(attachmentId: String) {
        httpClient.delete<String>("$apiBase/content/$attachmentId")
    }

    private fun FormBuilder.addAttachmentToForm(attachment: PageAttachmentInput) {
        append("comment", attachment.comment ?: "")
        append(
            "file",
            InputProvider(attachment.content.fileSize()) { attachment.content.toFile().inputStream().asInput() },
            Headers.build {
                attachment.contentType?.let { append(HttpHeaders.ContentType, it) }
                append(HttpHeaders.ContentDisposition, "filename=${attachment.name}")
            })
    }
}

private data class PageSearchResult(
    val results: List<ConfluencePage>,
    val start: Int,
    val limit: Int,
    val size: Int
)

fun confluenceClient(
    config: ConfluenceClientConfig
): ConfluenceClient {
    val client = HttpClient(CIO) {
        if (config.skipSsl) {
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    }
                }
            }
        }
        install(JsonFeature) {
            serializer = JacksonSerializer(
                jacksonObjectMapper()
                    .registerModule(Jdk8Module()).registerModule(JavaTimeModule())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            )
        }
        install(Auth) {
            config.auth.create(this)
        }
    }

    return ConfluenceClientImpl("${config.server}/rest/api", client)
}