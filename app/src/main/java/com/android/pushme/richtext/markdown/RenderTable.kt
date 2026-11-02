package com.android.pushme.richtext.markdown

import androidx.compose.runtime.Composable
import com.android.pushme.richtext.markdown.node.AstNode
import com.android.pushme.richtext.markdown.node.AstTableBody
import com.android.pushme.richtext.markdown.node.AstTableCell
import com.android.pushme.richtext.markdown.node.AstTableHeader
import com.android.pushme.richtext.markdown.node.AstTableRow
import com.android.pushme.richtext.ui.RichTextScope
import com.android.pushme.richtext.ui.Table

@Composable
internal fun RichTextScope.RenderTable(node: AstNode) {
  Table(
    headerRow = {
      node.filterChildrenType<AstTableHeader>()
        .firstOrNull()
        ?.filterChildrenType<AstTableRow>()
        ?.firstOrNull()
        ?.filterChildrenType<AstTableCell>()
        ?.forEach { tableCell ->
          cell {
            MarkdownRichText(tableCell)
          }
        }
    }
  ) {
    node.filterChildrenType<AstTableBody>()
      .firstOrNull()
      ?.filterChildrenType<AstTableRow>()
      ?.forEach { tableRow ->
        row {
          tableRow.filterChildrenType<AstTableCell>()
            .forEach { tableCell ->
              cell {
                MarkdownRichText(tableCell)
              }
            }
        }
      }
  }
}
