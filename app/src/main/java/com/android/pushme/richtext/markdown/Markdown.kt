package com.android.pushme.richtext.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.pushme.richtext.markdown.node.AstBlockQuote
import com.android.pushme.richtext.markdown.node.AstBulletList
import com.android.pushme.richtext.markdown.node.AstDocument
import com.android.pushme.richtext.markdown.node.AstFencedCodeBlock
import com.android.pushme.richtext.markdown.node.AstHeading
import com.android.pushme.richtext.markdown.node.AstHtmlBlock
import com.android.pushme.richtext.markdown.node.AstIndentedCodeBlock
import com.android.pushme.richtext.markdown.node.AstInlineNodeType
import com.android.pushme.richtext.markdown.node.AstLinkReferenceDefinition
import com.android.pushme.richtext.markdown.node.AstListItem
import com.android.pushme.richtext.markdown.node.AstNode
import com.android.pushme.richtext.markdown.node.AstOrderedList
import com.android.pushme.richtext.markdown.node.AstParagraph
import com.android.pushme.richtext.markdown.node.AstTableBody
import com.android.pushme.richtext.markdown.node.AstTableCell
import com.android.pushme.richtext.markdown.node.AstTableHeader
import com.android.pushme.richtext.markdown.node.AstTableRoot
import com.android.pushme.richtext.markdown.node.AstTableRow
import com.android.pushme.richtext.markdown.node.AstText
import com.android.pushme.richtext.markdown.node.AstThematicBreak
import com.android.pushme.richtext.ui.BasicRichText
import com.android.pushme.richtext.ui.BlockQuote
import com.android.pushme.richtext.ui.CodeBlock
import com.android.pushme.richtext.ui.FormattedList
import com.android.pushme.richtext.ui.Heading
import com.android.pushme.richtext.ui.HorizontalRule
import com.android.pushme.richtext.ui.ListType
import com.android.pushme.richtext.ui.RichTextScope
import com.android.pushme.richtext.ui.currentRichTextStyle
import com.android.pushme.richtext.ui.resolveDefaults
import com.android.pushme.richtext.ui.string.InlineContent
import com.android.pushme.richtext.ui.string.Text
import com.android.pushme.richtext.ui.string.richTextString

/**
 * A composable that renders Markdown content using RichText.
 *
 * @param content Markdown text. No restriction on length.
 * @param markdownParseOptions Options for the Markdown parser.
 * @param onLinkClicked A function to invoke when a link is clicked from rendered content.
 */
@Composable
public fun RichTextScope.Markdown(
  content: String,
  markdownParseOptions: MarkdownParseOptions = MarkdownParseOptions.Default,
  onLinkClicked: ((String) -> Unit)? = null,
  onTaskToggled: ((Int) -> Unit)? = null
) {
  val onLinkClickedState = rememberUpdatedState(onLinkClicked)
  val onTaskToggledState = rememberUpdatedState(onTaskToggled)
  // Can't use UriHandlerAmbient.current::openUri here,
  // see https://issuetracker.google.com/issues/172366483
  val realLinkClickedHandler = onLinkClickedState.value ?: LocalUriHandler.current.let {
    remember {
      { url -> it.openUri(url) }
    }
  }
  CompositionLocalProvider(
    LocalOnLinkClicked provides realLinkClickedHandler,
    LocalOnTaskToggled provides (onTaskToggledState.value ?: {})
  ) {
    val markdownAst = parsedMarkdownAst(text = content, options = markdownParseOptions)
    RecursiveRenderMarkdownAst(astNode = markdownAst)
  }
}

/**
 * Parse markdown content and return Abstract Syntax Tree(AST).
 * Composable is efficient thanks to remember construct.
 *
 * @param text Markdown text to be parsed.
 * @param options Options for the Markdown parser.
 */
//@Composable
//internal expect fun parsedMarkdownAst(text: String, options: MarkdownParseOptions): AstNode?

/**
 * When parsed, markdown content or any other rich text can be represented as a tree.
 * The default markdown parser that is used in this project `common-markdown` also
 * utilizes the said approach. Although there are ways to iteratively traverse a tree,
 * it is more readable and compose-friendly to do it recursively.
 *
 * This function basically receives a node from the tree, root or any node, and then
 * recursively travels along the nodes while spitting out or wrapping composables around
 * the content. RichText API is highly compatible with this method.
 *
 * However, there are multiple assumptions to increase predictability. Despite the fact
 * that every [AstNode] can have another [AstNode] as a child, it should not be that
 * generic in Markdown content. For example, a Text node must not have any other children.
 * That's why this function does not have 100% coverage for all [AstNode] types.
 *
 * Heading, Paragraph are considered to be main text containers. Their content is regarded
 * as one block and children traversal happens separately.
 *
 * FormattedList, OrderedList are also content blocks. Their children are filtered before
 * being traversed. Only ListItems are accepted as valid children for these blocks.
 *
 * For now, only tables are rendered from CustomBlock or CustomNode.
 *
 * @param astNode Root node to start rendering.
 */
@Suppress("IMPLICIT_CAST_TO_ANY")
@Composable
internal fun RichTextScope.RecursiveRenderMarkdownAst(astNode: AstNode?) {
  astNode ?: return

  when (val astNodeType = astNode.type) {
    is AstDocument -> visitChildren(node = astNode)
    is AstBlockQuote -> {
      BlockQuote {
        visitChildren(astNode)
      }
    }
    is AstBulletList -> {
      val listItems = astNode.filterChildrenType<AstListItem>().toList()
      val hasTaskItems = listItems.any { (it.type as AstListItem).taskChecked != null }
      if (hasTaskItems) {
        TaskFormattedList(
          items = listItems,
          onLinkClicked = LocalOnLinkClicked.current
        )
      } else {
        FormattedList(
          listType = ListType.Unordered,
          items = listItems
        ) {
          visitChildren(it)
        }
      }
    }
    is AstOrderedList -> {
      FormattedList(
        listType = ListType.Ordered,
        items = astNode.childrenSequence().toList()
      ) { astListItem ->
        visitChildren(astListItem)
      }
    }
    is AstThematicBreak -> {
      HorizontalRule()
    }
    is AstHeading -> {
      Heading(level = astNodeType.level) {
        MarkdownRichText(astNode, Modifier.semantics { heading() } )
      }
    }
    is AstIndentedCodeBlock -> {
      CodeBlock(text = astNodeType.literal.trim(), wordWrap = false)
    }
    is AstFencedCodeBlock -> {
      CodeBlock(text = astNodeType.literal.trim(), wordWrap = false)
    }
    is AstHtmlBlock -> {
      Text(text = richTextString {
        appendInlineContent(content = InlineContent {
          HtmlBlock(astNodeType.literal)
        })
      })
    }
    is AstLinkReferenceDefinition -> {
      // TODO(halilozercan)
      /* no-op */
    }
    is AstParagraph -> {
      MarkdownRichText(astNode)
    }
    is AstTableRoot -> {
      RenderTable(astNode)
    }
    // This should almost never happen. All the possible text
    // nodes must be under either Heading, Paragraph or CustomNode
    // In any case, we should include it here to prevent any
    // non-rendered text problems.
    is AstText -> {
      // TODO(halilozercan) use multiplatform compatible stderr logging
      println("Unexpected raw text while traversing the Abstract Syntax Tree.")
      Text(richTextString { append(astNodeType.literal) })
    }
    is AstListItem -> {
      println("MarkdownRichText: Unexpected AstListItem while traversing the Abstract Syntax Tree.")
    }
    is AstInlineNodeType -> {
      // ignore
      println("MarkdownRichText: Unexpected AstInlineNodeType $astNodeType while traversing the Abstract Syntax Tree.")
    }
    AstTableBody,
    AstTableHeader,
    AstTableRow,
    is AstTableCell -> {
      println("MarkdownRichText: Unexpected Table node while traversing the Abstract Syntax Tree.")
    }
  }.let {}
}

/**
 * Visit and render children from first to last.
 *
 * @param node Root ASTNode whose children will be visited.
 */
@Composable
internal fun RichTextScope.visitChildren(node: AstNode?) {
  node?.childrenSequence()?.forEach {
    RecursiveRenderMarkdownAst(astNode = it)
  }
}

/**
 * An internal ambient to pass through OnLinkClicked function from root [Markdown] composable
 * to children that render links. Although being explicit is preferred, recursive calls to
 * [visitChildren] increases verbosity with each extra argument.
 */
internal val LocalOnLinkClicked =
  compositionLocalOf<(String) -> Unit> { error("OnLinkClicked is not provided") }

/**
 * An internal ambient to pass through OnTaskToggled function from root [Markdown] composable
 * to [TaskFormattedList] for handling task checkbox toggle events.
 */
internal val LocalOnTaskToggled =
  compositionLocalOf<(Int) -> Unit> { {} }

/**
 * 渲染任务列表，每个任务项前显示复选框
 */
@Composable
internal fun RichTextScope.TaskFormattedList(
  items: List<AstNode>,
  onLinkClicked: (String) -> Unit
) {
  val density = LocalDensity.current
  val listStyle = currentRichTextStyle.resolveDefaults().listStyle!!
  val markerIndent = with(density) { listStyle.markerIndent!!.toDp() }
  val contentsIndent = with(density) { listStyle.contentsIndent!!.toDp() }
  val itemSpacingDp = with(density) { listStyle.itemSpacing!!.toDp() }
  val itemSpacingTextUnit = listStyle.itemSpacing
  val onTaskToggled = LocalOnTaskToggled.current

  // 计算每个任务项在整体任务列表中的索引
  var taskIndex = 0

  Column(
    verticalArrangement = Arrangement.spacedBy(itemSpacingDp)
  ) {
    items.forEach { listItem ->
      val astListItemType = listItem.type as AstListItem
      val isTask = astListItemType.taskChecked != null
      val isChecked = astListItemType.taskChecked == true
      val currentTaskIndex = if (isTask) taskIndex else -1
      if (isTask) taskIndex++

      if (isTask) {
        // 任务项：Checkbox + 内容
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(start = markerIndent)
        ) {
          val currentFontSize = LocalTextStyle.current.fontSize
          val checkboxScale = remember(currentFontSize) {
              if (currentFontSize.isSp) {
                  (currentFontSize.value / 20f).coerceIn(0.6f, 1f)
              } else {
                  1f
              }
          }
          Checkbox(
            checked = isChecked,
            onCheckedChange = {
              onTaskToggled(currentTaskIndex)
            },
            modifier = Modifier
              .padding(end = contentsIndent * checkboxScale)
              .requiredSize(24.dp)
              .scale(checkboxScale),
            colors = CheckboxDefaults.colors(
              checkedColor = MaterialTheme.colorScheme.primary,
              uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          )
          // 在 Checkbox 后渲染内容
          BasicRichText(
            style = currentRichTextStyle.copy(paragraphSpacing = itemSpacingTextUnit),
            modifier = Modifier.weight(1f)
          ) {
            listItem.childrenSequence().forEach { child ->
              RecursiveRenderMarkdownAst(child)
            }
          }
        }
      } else {
        // 非任务项，正常渲染
        BasicRichText(
          style = currentRichTextStyle.copy(paragraphSpacing = itemSpacingTextUnit)
        ) {
          visitChildren(listItem)
        }
      }
    }
  }
}
