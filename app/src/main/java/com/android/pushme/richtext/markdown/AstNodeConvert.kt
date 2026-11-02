package com.android.pushme.richtext.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.android.pushme.richtext.markdown.node.AstBlockQuote
import com.android.pushme.richtext.markdown.node.AstBulletList
import com.android.pushme.richtext.markdown.node.AstCode
import com.android.pushme.richtext.markdown.node.AstDocument
import com.android.pushme.richtext.markdown.node.AstEmphasis
import com.android.pushme.richtext.markdown.node.AstFencedCodeBlock
import com.android.pushme.richtext.markdown.node.AstHardLineBreak
import com.android.pushme.richtext.markdown.node.AstHeading
import com.android.pushme.richtext.markdown.node.AstHtmlBlock
import com.android.pushme.richtext.markdown.node.AstHtmlInline
import com.android.pushme.richtext.markdown.node.AstImage
import com.android.pushme.richtext.markdown.node.AstIndentedCodeBlock
import com.android.pushme.richtext.markdown.node.AstLink
import com.android.pushme.richtext.markdown.node.AstLinkReferenceDefinition
import com.android.pushme.richtext.markdown.node.AstListItem
import com.android.pushme.richtext.markdown.node.AstNode
import com.android.pushme.richtext.markdown.node.AstNodeLinks
import com.android.pushme.richtext.markdown.node.AstNodeType
import com.android.pushme.richtext.markdown.node.AstOrderedList
import com.android.pushme.richtext.markdown.node.AstParagraph
import com.android.pushme.richtext.markdown.node.AstSoftLineBreak
import com.android.pushme.richtext.markdown.node.AstStrikethrough
import com.android.pushme.richtext.markdown.node.AstStrongEmphasis
import com.android.pushme.richtext.markdown.node.AstTableBody
import com.android.pushme.richtext.markdown.node.AstTableCell
import com.android.pushme.richtext.markdown.node.AstTableCellAlignment
import com.android.pushme.richtext.markdown.node.AstTableHeader
import com.android.pushme.richtext.markdown.node.AstTableRoot
import com.android.pushme.richtext.markdown.node.AstTableRow
import com.android.pushme.richtext.markdown.node.AstText
import com.android.pushme.richtext.markdown.node.AstThematicBreak
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER
import org.commonmark.ext.gfm.tables.TableCell.Alignment.LEFT
import org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Converts common-markdown tree to AstNode tree in a recursive fashion.
 */
internal fun convert(
  node: Node?,
  parentNode: AstNode? = null,
  previousNode: AstNode? = null,
): AstNode? {
  node ?: return null

  val nodeLinks = AstNodeLinks(
    parent = parentNode,
    previous = previousNode,
  )

  val newNodeType: AstNodeType? = when (node) {
    is BlockQuote -> AstBlockQuote
    is BulletList -> AstBulletList(bulletMarker = node.marker[0])
    is Code -> AstCode(literal = node.literal)
    is Document -> AstDocument
    is Emphasis -> AstEmphasis(delimiter = node.openingDelimiter)
    is FencedCodeBlock -> AstFencedCodeBlock(
      literal = node.literal,
      fenceChar = node.fenceCharacter[0],
      fenceIndent = node.fenceIndent,
      fenceLength = node.openingFenceLength,
      info = node.info
    )
    is HardLineBreak -> AstHardLineBreak
    is Heading -> AstHeading(
      level = node.level
    )
    is ThematicBreak -> AstThematicBreak
    is HtmlInline -> AstHtmlInline(
      literal = node.literal
    )
    is HtmlBlock -> AstHtmlBlock(
      literal = node.literal
    )
    is Image -> {
      if (node.destination == null) {
        null
      }
      else {
        AstImage(
          title = node.title ?: "",
          destination = node.destination
        )
      }
    }
    is IndentedCodeBlock -> AstIndentedCodeBlock(
      literal = node.literal
    )
    is Link -> AstLink(
      title = node.title ?: "",
      destination = node.destination
    )
    is ListItem -> {
      // 检测是否为任务列表项，并跳过 TaskListItemMarker 子节点
      var taskChecked: Boolean? = null
      var firstContentChild: Node? = node.firstChild
      while (firstContentChild is TaskListItemMarker) {
        taskChecked = firstContentChild.isChecked
        firstContentChild = firstContentChild.next
      }
      AstListItem(taskChecked = taskChecked)
    }
    is OrderedList -> AstOrderedList(
      startNumber = node.markerStartNumber,
      delimiter = node.markerDelimiter[0]
    )
    is Paragraph -> AstParagraph
    is SoftLineBreak -> AstSoftLineBreak
    is StrongEmphasis -> AstStrongEmphasis(
      delimiter = node.openingDelimiter
    )
    is Text -> AstText(
      literal = node.literal
    )
    is LinkReferenceDefinition -> AstLinkReferenceDefinition(
      title = node.title ?: "",
      destination = node.destination,
      label = node.label
    )
    is TableBlock -> AstTableRoot
    is TableHead -> AstTableHeader
    is TableBody -> AstTableBody
    is TableRow -> AstTableRow
    is TableCell -> AstTableCell(
      header = node.isHeader,
      alignment = when (node.alignment) {
        LEFT -> AstTableCellAlignment.LEFT
        CENTER -> AstTableCellAlignment.CENTER
        RIGHT -> AstTableCellAlignment.RIGHT
        null -> AstTableCellAlignment.LEFT
        else -> AstTableCellAlignment.LEFT
      }
    )
    is Strikethrough -> AstStrikethrough(
      node.openingDelimiter
    )
    is CustomNode -> null
    is CustomBlock -> null
    else -> null
  }

  val newNode = newNodeType?.let {
    AstNode(newNodeType, nodeLinks)
  }

  if (newNode != null) {
    // 对于 ListItem，跳过 TaskListItemMarker 子节点（已在 AstListItem 中记录）
    val startChild = if (node is ListItem) {
      var child = node.firstChild
      while (child is TaskListItemMarker) {
        child = child.next
      }
      child
    } else {
      node.firstChild
    }
    newNode.links.firstChild = convert(startChild, parentNode = newNode, previousNode = null)
    newNode.links.next = convert(node.next, parentNode = parentNode, previousNode = newNode)
  }

  if (node.next == null) {
    parentNode?.links?.lastChild = newNode
  }

  return newNode
}

internal fun Node.convert() = convert(this)

@Composable
internal fun parsedMarkdownAst(text: String, options: MarkdownParseOptions): AstNode? {
  val parser = remember(options) {
    Parser.builder()
      .extensions(
        listOfNotNull(
          TablesExtension.create(),
          StrikethroughExtension.create(),
          TaskListItemsExtension.create(),
          if (options.autolink) AutolinkExtension.create() else null
        )
      )
      .build()
  }

  val astRootNode by produceState<AstNode?>(null, text, parser) {
    value = parser.parse(text).convert()
  }

  return astRootNode
}

