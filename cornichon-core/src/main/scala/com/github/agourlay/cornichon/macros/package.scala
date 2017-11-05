package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.dsl.BodyElementCollector

import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }

import scala.reflect.macros.{ TypecheckException, blackbox }

package object macros {
  class Macro(context: blackbox.Context) {
    val c = context

    import c.universe._

    def collectImpl(body: Tree) = {
      val contextType = c.prefix.tree.tpe
      val validContext = typeOf[BodyElementCollector[_, _]]

      if (!(contextType <:< validContext)) {
        c.abort(c.enclosingPosition, s"Macro only allowed to be used directly inside of a `$validContext`")
      } else {
        val elementType = contextType.typeArgs.head

        blockOrApplyExpressionList(body, elementType, s"Unsupported expression. Only expressions of type `$elementType` are allowed here.") { elements ⇒
          val (finalTree, rest) = elements.foldLeft((q"List.empty": Tree) → List.empty[Tree]) {
            case ((accTree, accSingle), elem) if elem.tpe <:< elementType ⇒
              accTree → (accSingle :+ elem)
            case ((accTree, accSingle), elem) ⇒
              q"$accTree ++ $accSingle ++ $elem" → Nil
          }

          q"${c.prefix.tree}.get($finalTree ++ $rest)"
        }
      }
    }

    private def blockOrApplyExpressionList(body: Tree, elementType: Type, unsupportedMessage: String)(typesTreesFn: List[Tree] ⇒ Tree) =
      body match {
        case block: Block ⇒
          blockExpressionList(block, elementType)(typesTreesFn)
        case app: Apply ⇒
          singleExpressionList(app, elementType, typesTreesFn)
        case Typed(app: Apply, _) ⇒
          singleExpressionList(app, elementType, typesTreesFn)
        case s @ Select(_, _) ⇒
          singleExpressionList(s, elementType, typesTreesFn)
        case e ⇒
          c.abort(e.pos, s"$unsupportedMessage\nfound '$e' of type '${e.tpe}'")
      }

    private def singleExpressionList(app: Tree, elementType: Type, typesTreesFn: List[Tree] ⇒ Tree) =
      typecheck(app, elementType) match {
        case Right(checked)     ⇒ typesTreesFn(checked :: Nil)
        case Left((pos, error)) ⇒ c.abort(pos, error)
      }

    private def blockExpressionList(block: Block, elementType: Type)(typesTreesFn: List[Tree] ⇒ Tree) = {
      val allStats = block.stats :+ block.expr
      val checked = allStats map (typecheck(_, elementType))
      val errors = checked.collect { case Left(error) ⇒ error }

      if (errors.nonEmpty) {
        errors.dropRight(1).foreach {
          case (pos, error) ⇒ c.error(pos, error)
        }

        c.abort(errors.last._1, errors.last._2)
      } else
        typesTreesFn(checked.collect { case Right(typed) ⇒ typed })
    }

    private def typecheck(tree: Tree, elementType: Type) = {
      val seq = seqType(elementType)
      val checked = c.typecheck(tree)

      if (checked.tpe <:< elementType || checked.tpe <:< seq)
        Right(checked)
      else
        try Right(c.typecheck(tree, pt = elementType)) catch {
          case TypecheckException(_, msg) ⇒ Left(tree.pos →
            (s"Result of this expression can be either `$elementType` or `$seq`. " + msg))
        }
    }

    private def seqType(elementType: Type) =
      c.typecheck(q"Seq[$elementType]()").tpe
  }
}
