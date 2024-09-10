package com.softwaremill.macwire

import com.softwaremill.macwire.internals.*
import scala.quoted.*
import scala.annotation.tailrec

object MacwireAutowireMacros {
  private val log = new Logger()

  def autowireImpl[T: Type](dependencies: Expr[Seq[Any]])(using q: Quotes): Expr[T] =
    import q.reflect.*

    val rawDependencies: Seq[Expr[Any]] = dependencies match
      case Varargs(exprs) => exprs
      case _              => report.errorAndAbort(s"Expected explicit varargs sequence, but got: $dependencies")

    // graph

    case class Node(
        tpe: TypeRepr,
        symbol: Symbol,
        dependencies: List[Symbol], // incoming edges
        genCode: () => Term
    )

    // fully resolved graph = for each node.dependency there's a node with that ident
    case class Graph(nodes: List[Node]):
      def findNodeForType(tpe: TypeRepr): Option[Symbol] = nodes.find(_.tpe <:< tpe).map(_.symbol)
      def addNode(n: Node): Graph = Graph(n :: nodes)

    // providers

    trait Provider:
      /** The type of instances that the provider providers. */
      def tpe: TypeRepr

      /** The types of dependencies that need to be provided, in order to create an instance of `tpe`. */
      def dependencies: List[TypeRepr]

      /** Given a list of trees, one corresponding to each dependency, returns a tree which creates an instance of
        * [[tpe]].
        */
      def create: List[Term] => Term

      /** The raw dependency as provided by the user, if any. */
      def raw: Option[Expr[Any]]
    case class InstanceProvider(
        tpe: TypeRepr,
        dependencies: List[TypeRepr],
        create: List[Term] => Term,
        raw: Option[Expr[Any]]
    ) extends Provider
    // trait ClassOfProvider
    // trait MembersOfProvider

    val providers =
      @tailrec
      def createProviders(deps: List[Expr[Any]], acc: Vector[Provider], seenTpes: Set[TypeRepr]): Vector[Provider] =
        deps match
          case Nil => acc
          case dep :: otherDeps =>
            val term = dep.asTerm
            val tpe = term.tpe
            if seenTpes.exists(seenTpe => seenTpe =:= tpe) then
              report.errorAndAbort("Duplicate type $tpe in dependencies list, for: $dep!")

            val instanceProvider = InstanceProvider(tpe, Nil, _ => term, Some(dep))

            val factoryProvider =
              if term.tpe.isFunctionType then Vector() // TODO
              else Vector.empty

            createProviders(otherDeps, (acc :+ instanceProvider) ++ factoryProvider, seenTpes + tpe)

      createProviders(rawDependencies.toList, Vector.empty, Set.empty)
    end providers

    def findOrGenerateProvider(t: TypeRepr): Option[Provider] =
      providers
        .find(_.tpe <:< t)
        .orElse:
          findConstructor[q.type](t, log).map: constructor =>
            InstanceProvider(t, constructor.paramFlatTypes, constructor.applied, None)

    // main logic

    /** Expands the given graph `g`, if necessary, so that it contains all nodes necessary to create an instance of `t`,
      * including the `t` instance.
      * @param breadcrumb
      *   The type path the leads to expanding the graph with the type `t`, excluding `t`.
      * @return
      *   The identifier in the updated graph, which corresponds to an instance of type `t`.
      */
    def expandGraph(t: TypeRepr, g: Graph, breadcrumb: Vector[TypeRepr]): (Symbol, Graph) =
      log.withBlock(s"wiring ${showTypeName(t)}"):
        // First, checking if the graph already contains a node for the given type. If so, we are done, no modifications to the graph.
        g.findNodeForType(t) match
          case Some(s) =>
            log(s"found in graph: $s")
            (s, g)
          case None =>
            verifyNotInProgress(t, breadcrumb)
            verifyNotPrimitive(t, breadcrumb)

            // Otherwise, looking for a provider which has a type <:< t; if not found, generating a provider based on a constructor/apply in companion.
            val tProvider = findOrGenerateProvider(t).getOrElse(
              report.errorAndAbort(s"Cannot find a dependency or a constructor for [$t]")
            )

            log(s"found provider $tProvider")

            // Recursively resolving each dependency, collecting dependency symbols
            val (updatedGraph, reverseSymbols) = tProvider.dependencies.foldLeft((g, Nil: List[Symbol])) {
              case ((currentGraph, symbolsAcc), dependency) =>
                val (dependencySymbol, updatedGraph) = expandGraph(dependency, currentGraph, breadcrumb :+ t)
                (updatedGraph, dependencySymbol :: symbolsAcc)
            }

            // Generating a new val-def symbol, adding it to the graph
            val dependencySymbols = reverseSymbols.reverse
            val tSymbol = Symbol.newVal(Symbol.spliceOwner, "autowire" + t.show, t, Flags.EmptyFlags, Symbol.noSymbol)
            val updatedGraph2 = updatedGraph.addNode(
              Node(t, tSymbol, dependencySymbols, () => tProvider.create(dependencySymbols.map(Ref(_))))
            )

            (tSymbol, updatedGraph2)
    end expandGraph

    // helper methods

    def verifyNotInProgress(t: TypeRepr, breadcrumb: Vector[TypeRepr]): Unit =
      for ip <- breadcrumb do
        if ip <:< t then report.errorAndAbort(s"Cyclic dependencies detected: ${showBreadcrumb(breadcrumb :+ t)}")

    def verifyNotPrimitive(t: TypeRepr, breadcrumb: Vector[TypeRepr]): Unit =
      t.asType match
        case '[Int] | '[Long] | '[Byte] | '[Short] | '[Char] | '[Boolean] | '[Double] | '[Float] | '[String] =>
          report.errorAndAbort(
            s"Cannot use a primitive type or String in autowiring: ${showBreadcrumb(breadcrumb :+ t)}"
          )
        case _ => // ok
    def showBreadcrumb(breadcrumb: Vector[TypeRepr]): String = breadcrumb.map(showTypeName).mkString(" -> ")

    def topologicalSort(graph: Graph): Graph =
      val nodes = graph.nodes
      val nodeMap = nodes.map(n => n.symbol -> n).toMap
      val visited = collection.mutable.Set.empty[Symbol]
      val result = collection.mutable.ListBuffer.empty[Node]

      def visit(n: Node): Unit =
        if !visited.contains(n.symbol) then
          visited += n.symbol
          n.dependencies.foreach { dep =>
            visit(nodeMap(dep))
          }
          result += n

      nodes.foreach(visit)
      Graph(result.toList)

    //

    val t = TypeRepr.of[T]
    val (rootSymbol, fullGraph) = expandGraph(t, Graph(Nil), Vector.empty)
    val sortedGraph = topologicalSort(fullGraph)

    // TODO: verify each dependency used

    val code = Block(
      sortedGraph.nodes.map(node => ValDef(node.symbol, Some(node.genCode()))),
      Ref(rootSymbol)
    )

    log(s"generated code: ${code.show}")
    code.asExprOf[T]

  // generate code from full graph, which then returns rootIdent

  end autowireImpl

}

// constructors

private def findConstructor[Q <: Quotes](using q: Q)(forType: q.reflect.TypeRepr, log: Logger): Option[Constructor[Q]] =
  import q.reflect.*

  def isAccessibleConstructor(s: Symbol) =
    s.isClassConstructor && !(s.flags is Flags.Private) && !(s.flags is Flags.Protected)

  /** In some cases there is one extra (phantom) constructor. This happens when extended trait has implicit param:
    *
    * {{{
    *   trait A { implicit val a = ??? };
    *   class X extends A
    *   import scala.reflect.runtime.universe._
    *   typeOf[X].members.filter(m => m.isMethod && m.asMethod.isConstructor && m.asMethod.isPrimaryConstructor).map(_.asMethod.fullName)
    *
    *   //res1: Iterable[String] = List(X.<init>, A.$init$)
    * }}}
    *
    * The {{{A.$init$}}} is the phantom constructor and we don't want it.
    *
    * In other words, if we don't filter such constructor using this function 'wireActor-12-noPublicConstructor.failure'
    * will compile and throw exception during runtime but we want to fail it during compilation time.
    */
  def isPhantomConstructor(constructor: Symbol): Boolean = constructor.fullName.endsWith("$init$")

  val publicConstructors: Iterable[Symbol] =
    val ctors = forType.typeSymbol.declarations
      .filter(isAccessibleConstructor)
      .filterNot(isPhantomConstructor)
    log.withBlock(s"There are ${ctors.size} eligible constructors") { ctors.foreach(c => log(c.toString)) }
    ctors

  val primaryConstructor: Option[Symbol] = forType.typeSymbol.primaryConstructor match
    case c if isAccessibleConstructor(c) => Some(c)
    case c                               => None

  val injectConstructors: Iterable[Symbol] =
    val isInjectAnnotation = (a: Term) => a.tpe.typeSymbol.fullName == "javax.inject.Inject"
    val ctors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
    log.withBlock(s"There are ${ctors.size} constructors annotated with @javax.inject.Inject") {
      ctors.foreach(c => log(c.toString))
    }
    ctors

  val injectConstructor: Option[Symbol] =
    if injectConstructors.size > 1 then
      report.errorAndAbort(
        s"Ambiguous constructors annotated with @javax.inject.Inject for type [${forType.typeSymbol.name}]"
      )
    else injectConstructors.headOption

  log.withBlock(s"Looking for constructor for $forType"):
    (injectConstructor orElse primaryConstructor).map: ctor =>
      log(s"Found $ctor")
      Constructor[Q](ctor, forType)

private class Constructor[Q <: Quotes](using val q: Q)(
    constructorSymbol: q.reflect.Symbol,
    forType: q.reflect.TypeRepr
):
  import q.reflect.*

  private val paramSymbolsLists: List[List[Symbol]] = constructorSymbol.paramSymss

  // all non-implicit parmaters
  val paramFlatTypes: List[TypeRepr] = paramSymbolsLists.flatMap(
    _.flatMap(param => if param.flags is Flags.Implicit then None else Some(paramType(param)))
  )

  def applied(paramFlatValues: List[Term]): Term =
    val paramsFlatValuesIterator = paramFlatValues.iterator
    val paramValuesLists = paramSymbolsLists.map { paramSymbolList =>
      paramSymbolList.map { paramSymbol =>
        if paramSymbol.flags is Flags.Implicit
        then resolveImplicitOrFail(paramSymbol)
        else paramsFlatValuesIterator.next()
      }
    }
    val newTree: Term = Select(New(TypeIdent(forType.typeSymbol)), constructorSymbol)
    paramValuesLists.foldLeft(newTree)((acc: Term, args: List[Term]) => Apply(acc, args))

  private def resolveImplicitOrFail(param: Symbol): Term = Implicits.search(paramType(param)) match {
    case iss: ImplicitSearchSuccess => iss.tree
    case isf: ImplicitSearchFailure => report.errorAndAbort(s"Failed to resolve an implicit for [$param].")
  }

  private def paramType(param: Symbol): TypeRepr = Ref(param).tpe.widen.dealias
