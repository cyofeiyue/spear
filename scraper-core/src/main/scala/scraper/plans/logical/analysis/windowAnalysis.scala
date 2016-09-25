package scraper.plans.logical.analysis

import scraper._
import scraper.expressions._
import scraper.expressions.InternalAlias.buildRewriter
import scraper.expressions.windows.WindowFunction
import scraper.plans.logical._
import scraper.plans.logical.analysis.WindowAnalysis._

class ExtractWindowFunctionsFromProjects(val catalog: Catalog) extends AnalysisRule {
  override def apply(tree: LogicalPlan): LogicalPlan = tree transformDown {
    case Resolved(child Project projectList) if hasWindowFunction(projectList) =>
      val winAliases = collectWindowFunctions(projectList) map (WindowAlias(_))
      val rewrittenProjectList = projectList map (_ transformDown buildRewriter(winAliases))
      child windows winAliases select rewrittenProjectList
  }
}

class ExtractWindowFunctionsFromSorts(val catalog: Catalog) extends AnalysisRule {
  override def apply(tree: LogicalPlan): LogicalPlan =
    tree collectFirst preConditionViolation map (_ => tree) getOrElse {
      tree transformDown {
        case Resolved(child Sort order) if hasWindowFunction(order) =>
          val winAliases = collectWindowFunctions(order) map (WindowAlias(_))
          val rewrittenOrder = order map (_ transformDown buildRewriter(winAliases))
          child windows winAliases orderBy rewrittenOrder select child.output
      }
    }

  private val preConditionViolation: PartialFunction[LogicalPlan, Unit] = {
    case _: UnresolvedAggregate =>
  }
}

object WindowAnalysis {
  def hasWindowFunction(expressions: Seq[Expression]): Boolean =
    expressions exists hasWindowFunction

  /**
   * Collects all distinct window functions from `expressions`.
   */
  def collectWindowFunctions(expressions: Seq[Expression]): Seq[WindowFunction] =
    expressions.flatMap(_.collect { case f: WindowFunction => f }).distinct

  private def hasWindowFunction(expression: Expression): Boolean =
    expression.collectFirst { case _: WindowFunction => }.nonEmpty
}