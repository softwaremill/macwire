package com.softwaremill

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import dom.document

object MacwireScalajsExample extends JSApp with MainModule {
  @JSExport
  def buttonClicked(): Unit = {
    val result = earnLotsOfMoney.doIt()
    val objectGraph = earnLotsOfMoney.toString
    appendTextInParagraph(document.body, s"$result, object graph: $objectGraph")
  }

  private def appendTextInParagraph(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  override def main() {}
}
