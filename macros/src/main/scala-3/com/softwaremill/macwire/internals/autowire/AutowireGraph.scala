package com.softwaremill.macwire.internals.autowire

import scala.quoted.Quotes
import scala.quoted.Expr

class AutowireGraph[Q <: Quotes](using val q: Q):
  import q.reflect.*

  case class Node(
      tpe: TypeRepr, // the type of the instance that will be created
      symbol: Symbol, // the symbol of the val-def that will hold the instance
      dependencies: List[Symbol], // incoming edges: the nodes for types that need to be created before this one
      createInstance: Term, // code to create an instance of `tpe`
      raw: Option[Expr[Any]] // the raw dependency as provided by the user, if any
  )

  /** A fully resolved graph, i.e. for each node's dependency there's a node with that symbol. */
  case class Graph(nodes: Vector[Node]):
    def findNodeForType(tpe: TypeRepr): Option[Symbol] = nodes.find(_.tpe <:< tpe).map(_.symbol)
    def addNode(n: Node): Graph = Graph(nodes :+ n)
