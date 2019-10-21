package com.github.agourlay.cornichon.dsl

import scala.reflect.macros.{ TypecheckException, blackbox }

class BodyElementCollectorMacro(context: blackbox.Context) {
  val c: blackbox.Context = context

  import c.universe._

  private val initTreeFold: (Tree, List[Tree]) = q"Nil" → Nil

  def collectImpl(body: Tree): Tree = {
    val contextType = c.prefix.tree.tpe
    val validContext = typeOf[BodyElementCollector[_, _]]

    if (!(contextType <:< validContext))
      c.abort(c.enclosingPosition, s"Macro only allowed to be used directly inside of a `$validContext`")
    else {
      val elementType = contextType.typeArgs.head
      blockOrApplyExpressionList(body, elementType) { elements ⇒
        val (finalTree, rest) = elements.foldLeft(initTreeFold) {
          case ((accTree, accSingle), elem) ⇒
            if (elem.tpe <:< elementType)
              accTree → (accSingle :+ elem) //todo avoid List.append
            else
              q"$accTree ++ $accSingle ++ $elem" → Nil
        }

        q"${c.prefix.tree}.get($finalTree ++ $rest)"
      }
    }
  }

  private def blockOrApplyExpressionList(body: Tree, elementType: Type)(typesTreesFn: List[Tree] ⇒ Tree): c.universe.Tree =
    body match {
      case block: Block ⇒
        blockExpressionList(block, elementType)(typesTreesFn)
      case app: Apply ⇒
        singleExpressionList(app, elementType)(typesTreesFn)
      case Typed(app: Apply, _) ⇒
        singleExpressionList(app, elementType)(typesTreesFn)
      case s @ Select(_, _) ⇒
        singleExpressionList(s, elementType)(typesTreesFn)
      case e ⇒
        val unsupportedMessage = s"Unsupported expression. Only expressions of type `$elementType` are allowed here."
        c.abort(e.pos, s"$unsupportedMessage\nfound '$e' of type '${e.tpe}'")
    }

  private def singleExpressionList(app: Tree, elementType: Type)(typesTreesFn: List[Tree] ⇒ Tree): c.universe.Tree =
    typeCheck(elementType, seqElementType(elementType))(app) match {
      case Right(checked)     ⇒ typesTreesFn(checked :: Nil)
      case Left((pos, error)) ⇒ c.abort(pos, error)
    }

  private def seqElementType(elementType: Type): Type =
    c.typecheck(q"Seq[$elementType]()").tpe

  private def blockExpressionList(block: Block, elementType: Type)(typesTreesFn: List[Tree] ⇒ Tree): c.universe.Tree = {
    val allStats = block.stats :+ block.expr //todo avoid List.append
    val seq = seqElementType(elementType)
    val checked = allStats.map(typeCheck(elementType, seq))

    if (checked.exists(_.isLeft)) {
      val errors = checked.collect { case Left(error) ⇒ error }
      errors.dropRight(1).foreach { case (pos, error) ⇒ c.error(pos, error) }
      val lastError = errors.last
      c.abort(lastError._1, lastError._2)
    } else
      typesTreesFn(checked.collect { case Right(typed) ⇒ typed })
  }

  private def typeCheck(elementType: Type, seq: Type)(tree: Tree): Either[(c.universe.Position, String), c.Tree] = {
    val checked = c.typecheck(tree)
    // checked.tpe is null if the statement is an import
    if (checked.tpe == null)
      Left(tree.pos → s"Expected expression of either `$elementType` or `$seq` but found '$tree'")
    else if (checked.tpe <:< elementType || checked.tpe <:< seq)
      Right(checked)
    else
      try Right(c.typecheck(tree, pt = elementType)) catch {
        case TypecheckException(_, msg) ⇒ Left(tree.pos →
          (s"Result of this expression can be either `$elementType` or `$seq`. " + msg))
      }
  }
}
