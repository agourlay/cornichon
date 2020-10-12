package com.github.agourlay.cornichon.dsl

import scala.collection.mutable.ListBuffer
import scala.reflect.macros.{ TypecheckException, blackbox }

class BodyElementCollectorMacro(context: blackbox.Context) {
  val c: blackbox.Context = context

  import c.universe._

  def collectImpl(body: Tree): Tree = {
    val contextType = c.prefix.tree.tpe
    val validContext = typeOf[BodyElementCollector[_, _]]

    if (!(contextType <:< validContext))
      c.abort(c.enclosingPosition, s"Macro only allowed to be used directly inside of a `$validContext`")
    else {
      val elementType = contextType.typeArgs.head
      blockOrApplyExpressionList(body, elementType) { elements =>
        val elementBuffer = ListBuffer.empty[Tree]
        var seqElementTree: Option[Tree] = None

        elements.foreach { elem =>
          if (elem.tpe <:< elementType)
            elementBuffer += elem
          else {
            // the only other type authorized here is Seq[Element]
            val elementsSoFar = elementBuffer.result()
            elementBuffer.clear()
            seqElementTree match {
              case None =>
                seqElementTree = Some(q"$elementsSoFar ++ $elem")
              case Some(seqTree) =>
                seqElementTree = Some(q"$seqTree ++ $elementsSoFar ++ $elem")
            }
          }
        }
        val elementTrees = elementBuffer.result()
        seqElementTree match {
          case None =>
            q"${c.prefix.tree}.get($elementTrees)"
          case Some(seqElementTrees) =>
            q"${c.prefix.tree}.get($seqElementTrees ++ $elementTrees)"
        }
      }
    }
  }

  private def blockOrApplyExpressionList(body: Tree, elementType: Type)(typesTreesFn: List[Tree] => Tree): c.universe.Tree =
    body match {
      case block: Block =>
        blockExpressionList(block, elementType)(typesTreesFn)
      case app: Apply =>
        singleExpressionList(app, elementType)(typesTreesFn)
      case Typed(app: Apply, _) =>
        singleExpressionList(app, elementType)(typesTreesFn)
      case s @ Select(_, _) =>
        singleExpressionList(s, elementType)(typesTreesFn)
      case l: Ident =>
        singleExpressionList(l, elementType)(typesTreesFn)
      case e =>
        val unsupportedMessage = s"Unsupported expression. Only expressions of type `$elementType` are allowed here."
        c.abort(e.pos, s"$unsupportedMessage\nfound '$e' of type '${e.tpe}'")
    }

  private def singleExpressionList(app: Tree, elementType: Type)(typesTreesFn: List[Tree] => Tree): c.universe.Tree =
    typeCheck(elementType, seqElementType(elementType))(app) match {
      case Right(checked)     => typesTreesFn(checked :: Nil)
      case Left((pos, error)) => c.abort(pos, error)
    }

  private def seqElementType(elementType: Type): Type =
    c.typecheck(q"Seq[$elementType]()").tpe

  private def blockExpressionList(block: Block, elementType: Type)(typesTreesFn: List[Tree] => Tree): c.universe.Tree = {
    val seq = seqElementType(elementType)
    val validExpressions = List.newBuilder[Tree]
    val errors = List.newBuilder[(c.universe.Position, String)]

    def evalTree(s: Tree) =
      typeCheck(elementType, seq)(s) match {
        case Right(tree) => validExpressions += tree
        case Left(e)     => errors += e
      }

    // block.stats (all but last)
    block.stats.foreach(evalTree)
    // block.expr (last)
    evalTree(block.expr)

    val blockErrors = errors.result()
    if (blockErrors.nonEmpty) {
      blockErrors.dropRight(1).foreach { case (pos, error) => c.error(pos, error) }
      val lastError = blockErrors.last
      c.abort(lastError._1, lastError._2)
    } else
      typesTreesFn(validExpressions.result())
  }

  private def typeCheck(elementType: Type, seq: Type)(tree: Tree): Either[(c.universe.Position, String), c.Tree] = {
    val checked = c.typecheck(tree)
    // checked.tpe is null if the statement is an import
    if (checked.tpe == null)
      Left(tree.pos -> s"Expected expression of either `$elementType` or `$seq` but found '$tree'")
    else if (checked.tpe <:< elementType || checked.tpe <:< seq)
      Right(checked)
    else
      try Right(c.typecheck(tree, pt = elementType)) catch {
        case TypecheckException(_, msg) => Left(tree.pos ->
          (s"Result of this expression can be either `$elementType` or `$seq`. " + msg))
      }
  }
}
