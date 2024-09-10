package com.softwaremill.macwire.internals.autowire

import scala.quoted.Quotes

class AutowireGraph[Q <: Quotes](using val q: Q):
  import q.reflect.*

  case class Node(
      tpe: TypeRepr, // the type of the instance that will be created
      symbol: Symbol, // the symbol of the val-def that will hold the instance
      dependencies: List[Symbol], // incoming edges: the nodes for types that need to be created before this one
      createInstance: Term // code to create an instance of `tpe`
  )

  /** A fully resolved graph, i.e. for each node's dependency there's a node with that symbol. */
  case class Graph(nodes: List[Node]):
    def findNodeForType(tpe: TypeRepr): Option[Symbol] = nodes.find(_.tpe <:< tpe).map(_.symbol)
    def addNode(n: Node): Graph = Graph(n :: nodes)
    def sortTopological: Graph =
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
