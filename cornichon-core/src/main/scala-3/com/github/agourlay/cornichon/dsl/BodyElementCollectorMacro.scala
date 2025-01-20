package com.github.agourlay.cornichon.dsl

object BodyElementCollectorMacro {

  import scala.quoted.*

  // we need to extract bodies from the bodyExpr and pass them to the fnExpr
  // depending on the type of the bodyExpr, we can either extract the bodies at compile-time or at runtime for non-literal Seq[Body]
  sealed trait ExtractedBodies[Body]
  object ExtractedBodies {
    case class CompileTimeBodies[Body](items: List[Expr[Body]]) extends ExtractedBodies[Body]
    case class RuntimeBodies[Body](expr: Expr[List[Body]]) extends ExtractedBodies[Body]
  }

  import ExtractedBodies.*

  def collectOneImpl[Body: Type, Result: Type](
      bodyExpr: Expr[Any],
      fnExpr:   Expr[List[Body] => Result]
    )(using Quotes): Expr[Result] = {
      import quotes.reflect.*

      val termBody = bodyExpr.asTerm match {
        case Inlined(_, _, underlying) => underlying
        case other                     => other
      }

      val (stats, last) = termBody match {
        case Block(sts, l) => (sts, l)
        case single        => (Nil, single)
      }

      val terms = stats.flatMap {
        case t: Term => Some(t)
        case other =>
          report.error(s"Unsupported statement in body: ${other.show}")
          None
      }

      val allStatements = terms :+ last

      def stmtToBodies[Body: Type](stmt: Term)(using Quotes): ExtractedBodies[Body] = {

        import quotes.reflect.*

        val exprAny: Expr[Any] = stmt.asExpr
        val maybeBody: Option[Expr[Body]] = exprAny.asExprOfSafe[Body]
        val maybeSeqBody: Option[Expr[Seq[Body]]] =  exprAny.asExprOfSafe[Seq[Body]]

        (maybeBody, maybeSeqBody) match {
          case (Some(bodyExpr), _) => CompileTimeBodies(List(bodyExpr))
          case (None, Some(seqExpr)) =>
            val expanded = expandSeqIfLiteral(seqExpr)
            expanded match {
              case Some(listOfExprBody) => CompileTimeBodies(listOfExprBody)
              case None => RuntimeBodies('{ $seqExpr.toList })
            }

          case (None, None) =>
            report.error(s"Expression is neither a Body nor a Seq[Body]. Found: ${stmt.show}")
            CompileTimeBodies(Nil)
        }
      }

      val extractedList: List[ExtractedBodies[Body]] = allStatements.map(stmtToBodies[Body])

      val finalResult: Expr[List[Body]] = mergeAllBodies(extractedList)

      '{ $fnExpr($finalResult) }

  }


  extension (expr: Expr[Any]) {
    def asExprOfSafe[T: Type](using Quotes): Option[Expr[T]] =
      import quotes.reflect.*

      val tpeT = TypeRepr.of[T]
      if (expr.asTerm.tpe <:< tpeT) Some(expr.asExprOf[T]) else None
  }

  def mergeAllBodies[Body: Type](extracted: List[ExtractedBodies[Body]])(using Quotes): Expr[List[Body]] = {
    import quotes.reflect.*

    // the final expression we will return
    var finalExpr: Expr[List[Body]] = '{ List.empty[Body] }
    // compile time accumulation (not an expression)
    var compileTimeAcc = List.empty[Expr[Body]]

    def flushCompileTimeToFinal(): Unit = {
      if (compileTimeAcc.nonEmpty) {
        val ctExpr: Expr[List[Body]] = Expr.ofList(compileTimeAcc)
        finalExpr = '{ $finalExpr ++ $ctExpr }
        compileTimeAcc = Nil
      }
    }

    for (ex <- extracted) {
      ex match {
        // compile time accumulation
        case CompileTimeBodies(items) => compileTimeAcc = compileTimeAcc ++ items
        // runtime accumulation
        case RuntimeBodies(rExpr) =>
          flushCompileTimeToFinal()
          finalExpr = '{ $finalExpr ++ $rExpr }
      }
    }

    // flush to ensure we don't forget the last compile-time accumulation
    flushCompileTimeToFinal()

    finalExpr
  }


  private def expandSeqIfLiteral[Body: Type](seqExpr: Expr[Seq[Body]])(using Quotes): Option[List[Expr[Body]]] = {
    import quotes.reflect.*

    val seqTerm = seqExpr.asTerm
    seqTerm match {
      // this is a literal Seq
      case Inlined(_, _, Apply(TypeApply(Select(qual, "apply"), _), args)) if isSeqQual(qual) =>
        val bodies = args.flatMap { argTerm =>
          val exprAny: Expr[Any] = argTerm.asExpr
          exprAny.asExprOfSafe[Body]
            .map(List(_))
            .getOrElse {
              report.error(s"Element in Seq is not of type Body: ${argTerm.show}")
              Nil
            }
        }
        Some(bodies)
      // not a literal Seq
      case _ => None
    }
  }

  private def isSeqQual(using Quotes)(qual: quotes.reflect.Term): Boolean = {
    import quotes.reflect.*
    qual match {
      case Ident("Seq") => true
      case Select(Ident("scala" | "collection" | "immutable"), "Seq") => true
      case _ => false
    }
  }

}
