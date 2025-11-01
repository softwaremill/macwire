package com.softwaremill.macwire.internals.autowire

import com.softwaremill.macwire.internals.*
import scala.quoted.*

private val log = new Logger()

def autowireImpl[T: Type](dependencies: Expr[Seq[Any]])(using q: Quotes): Expr[T] =
  import q.reflect.*

  val ag = new AutowireGraph[q.type]
  import ag.*

  // extracting the provided varargs - we need an explicitly provided list of dependencies
  val rawDependencies: Seq[Expr[Any]] = dependencies match
    case Varargs(exprs) => exprs
    case _              =>
      report.errorAndAbort(
        s"depedencies need to be provided directly as parameters to the autowire call; got: $dependencies"
      )

  val reportError = new ReportError[q.type]

  val ap = new AutowireProviders[q.type](rawDependencies.toList, reportError)
  import ap.*

  val uniqueNames = new UniqueNames

  //

  /** Expands the given graph `g`, if necessary, so that it contains all nodes necessary to create an instance of `t`,
    * including the `t` instance.
    * @param breadcrumb
    *   The type path the leads to expanding the graph with the type `t`, excluding `t`.
    * @return
    *   The symbol in the updated graph, which corresponds to an instance of type `t`, and the updated graph.
    */
  def expandGraph(t: TypeRepr, g: Graph, breadcrumb: Vector[TypeRepr]): (Symbol, Graph) =
    log.withBlock(s"wiring ${showTypeName(t)}"):
      reportError.withType(t):
        // First, checking if the graph already contains a node for the given type. If so, we are done, no modifications to the graph.
        g.findNodeForType(t) match
          case Some(s) =>
            log(s"found in graph: $s")
            (s, g)
          case None =>
            verifyNotInProgress(t, breadcrumb)
            verifyNotPrimitive(t, breadcrumb)

            // Otherwise, looking for a provider which has a type <:< t; if not found, generating a provider based on a constructor/apply in companion.
            val tProvider = Provider
              .forType(t)
              .getOrElse(
                reportError(
                  s"cannot find a provided dependency, public constructor or public apply method for: ${showTypeName(t)}"
                )
              )

            // Recursively resolving each dependency, collecting dependency symbols
            val (updatedGraph, reverseSymbols) = tProvider.dependencies.foldLeft((g, Nil: List[Symbol])) {
              case ((currentGraph, symbolsAcc), dependency) =>
                val (dependencySymbol, updatedGraph) = expandGraph(dependency, currentGraph, breadcrumb :+ t)
                (updatedGraph, dependencySymbol :: symbolsAcc)
            }

            // Generating a new val-def symbol, adding it to the graph
            val dependencySymbols = reverseSymbols.reverse
            val tSymbol =
              Symbol.newVal(
                Symbol.spliceOwner,
                uniqueNames.next("wired_" + showTypeName(t)),
                t,
                Flags.EmptyFlags,
                Symbol.noSymbol
              )
            val updatedGraph2 = updatedGraph.addNode(
              Node(t, tSymbol, dependencySymbols, tProvider.create(dependencySymbols.map(Ref(_))), tProvider.raw)
            )

            (tSymbol, updatedGraph2)
  end expandGraph

  // helper methods

  def verifyNotInProgress(t: TypeRepr, breadcrumb: Vector[TypeRepr]): Unit =
    for ip <- breadcrumb do if ip <:< t then reportError("cyclic dependencies detected")

  def verifyNotPrimitive(t: TypeRepr, breadcrumb: Vector[TypeRepr]): Unit =
    t.asType match
      case '[Int] | '[Long] | '[Byte] | '[Short] | '[Char] | '[Boolean] | '[Double] | '[Float] | '[String] =>
        reportError("cannot use a primitive type or String in autowiring")
      case _ => // ok
  end verifyNotPrimitive

  def verifyEachDependencyUsed(nodes: Vector[Node]): Unit =
    val usedDependencies = nodes.flatMap(_.raw).toSet
    val unusedDependencies = rawDependencies.filterNot(usedDependencies.contains)
    if unusedDependencies.nonEmpty then
      reportError(
        s"unused dependencies: ${unusedDependencies.map(showExprShort).mkString(", ")}"
      )

  //

  val t = TypeRepr.of[T]
  val (rootSymbol, fullGraph) = expandGraph(t, Graph(Vector.empty), Vector.empty)

  // the graph is already sorted topologically: nodes are appended, and added only once all dependencies are present

  verifyEachDependencyUsed(fullGraph.nodes)

  val code = Block(
    fullGraph.nodes.map(node => ValDef(node.symbol, Some(node.createInstance))).toList,
    Ref(rootSymbol)
  )

  log(s"generated code: ${code.show}")
  code.asExprOf[T]
end autowireImpl
