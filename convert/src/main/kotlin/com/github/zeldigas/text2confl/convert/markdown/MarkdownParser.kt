package com.github.zeldigas.text2confl.convert.markdown

import com.github.zeldigas.text2confl.convert.Attachment
import com.github.zeldigas.text2confl.convert.ConvertingContext
import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.KeepType
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.MutableDataSet
import java.io.BufferedReader
import java.nio.file.Path

internal class MarkdownParser {

    private val standardExtensions = listOf(
        TablesExtension.create(), YamlFrontMatterExtension.create(),
        TaskListExtension.create(), StrikethroughSubscriptExtension.create(),
        AttributesExtension.create()
    )
    private val parserOptions: DataHolder = MutableDataSet()
        .set(Parser.REFERENCES_KEEP, KeepType.LAST)
        .set(HtmlRenderer.INDENT_SIZE, 2)
        .set(HtmlRenderer.PERCENT_ENCODE_URLS, true)

        // for full GFM table compatibility add the following table extension options:
        .set(TablesExtension.COLUMN_SPANS, false)
        .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
        .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
        .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
        .set(
            Parser.EXTENSIONS, standardExtensions
        )
        .toImmutable()

    private val parser = Parser.builder(parserOptions).build()

    fun parseReader(reader: BufferedReader): Document {
        return parser.parseReader(reader)
    }

    fun parseString(document: String): Document {
        return parser.parse(document)
    }

    fun htmlRenderer(location: Path, attachments: Map<String, Attachment>, context: ConvertingContext): HtmlRenderer {
        return HtmlRenderer.builder(
            parserOptions.toMutable()
                .set(HtmlRenderer.RENDER_HEADER_ID, true)
                .set(Parser.EXTENSIONS, standardExtensions + listOf(ConfluenceFormatExtension()))
                .set(ConfluenceFormatExtension.DOCUMENT_LOCATION, location)
                .set(ConfluenceFormatExtension.ATTACHMENTS, attachments)
                .set(ConfluenceFormatExtension.CONTEXT, context)
                .toImmutable()
        ).build()
    }


}