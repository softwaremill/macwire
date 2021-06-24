package com.softwaremill.macwire.internals

import scala.annotation.tailrec
import scala.quoted.*

private[macwire] class EligibleValuesFinder[Q <: Quotes](log: Logger)(using val q: Q) {

  import q.reflect.*
  import EligibleValuesFinder.*

    private val typeCheckUtil = new TypeCheckUtil[q.type](log)
  import typeCheckUtil._

  def find(): EligibleValues = {
    val wiredDef = Symbol.spliceOwner.owner
    val wiredOwner = wiredDef.owner

    val classScopeValues = (wiredOwner.memberMethods ::: wiredOwner.memberFields)
        .filter(m => !m.fullName.startsWith("java.lang.Object") && !m.fullName.startsWith("scala.Any"))
        .map(_.tree).collect {
            case m: ValDef => EligibleValue(m.rhs.map(_.tpe).getOrElse(m.tpt.tpe), m)
            case m: DefDef if m.termParamss.flatMap(_.params).isEmpty =>
                EligibleValue(m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe), m)
    }

    EligibleValues(Map(Scope.Class -> classScopeValues))
  }

//   /** @return all the members of all the parents */
//   private def registerParentsMembers(values: EligibleValues): EligibleValues = {
//     val parents = c.enclosingClass match {
//       case ClassDef(_, _, _, Template(pp, self, _)) =>
//         val selfTypes = self.tpt match {
//           case ident : Ident => List(ident)
//           case CompoundTypeTree(Template(selfParents,_,_)) => selfParents
//           case x : Select if x.isType => List(x)

//           // Self types with type parameters
//           case ta : AppliedTypeTree => List(ta)

//           case _ => Nil
//         }
//         pp ++ selfTypes

//       case ModuleDef(_, _, Template(pp, _, _)) => pp
//       case e =>
//         c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
//         Nil
//     }

//     parents.foldLeft(values) { case (newValues,parent) =>
//       val tpe: Tree = parent match {
//         case q"$tpe(..$params)" => tpe // ignore parameters passed to the parent
//         case q"$tpe" => tpe
//       }
//       if (tpe.symbol.fullName == "scala.AnyRef") {
//         newValues
//       } else {
//         log.withBlock(s"Inspecting parent $tpe members") {

//           val root = typeCheckIfNeeded(tpe)

//           root.members.
//             filter(filterMember).
//             foldLeft(newValues) { case (newValues, symbol) =>

//             // Get a view of this symbol as seen from the enclosing class
//             // This ensures that type parameters are resolved correctly in parent traits.
//             // See - https://github.com/adamw/macwire/issues/126
//             val found = symbol.typeSignatureIn( root )

//             newValues.put(Scope.ParentOrModule, found,
//               Ident(TermName(symbol.name.decodedName.toString.trim()))) // q"$symbol" crashes the compiler...
//           }
//         }
//       }
//     }
//   }

//   private def hasModuleAnnotation(symbol: Symbol) : Boolean = {
//     symbol.annotations.exists { annotation =>
//       annotation.tree match {
//         case q"new $parent()" => parent.symbol.fullName == "com.softwaremill.macwire.Module"
//         case _ => false
//       }
//     }
//   }

//   /** @return (statements-before-wire, statements-after-wire) */
//   private def partitionStatementsAfterWireCall(statements: List[Tree]): (List[Tree], List[Tree]) = {
//     statements.partition { _.pos.end <= c.enclosingPosition.start }
//   }

//   private def filterImportMembers[T](members: List[(Symbol,T)]) : List[T] = {
//     members.collect { case (m,t) if filterMember(m) => t }
//   }

//   private def filterMember(member: Symbol) : Boolean = {
//     !member.fullName.startsWith("java.lang.Object") &&
//     !member.fullName.startsWith("scala.Any") &&
//     !member.fullName.endsWith("<init>") &&
//     !member.fullName.endsWith("$init$") &&
//     member.isPublic
//   }

//   private def treeToCheck(tree: Tree, rhs: Tree) = {
//     // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
//     // type ascription needed errors from the compiler.
//     if (rhs.isEmpty) tree else rhs
//   }

//   private def extractMatchingParams(params: List[ValDef]): List[(Tree,Tree)] = params.collect {
//     case param@ValDef(_, name, tpt, _) => (Ident(name), treeToCheck(param, tpt))
//   }

  case class EligibleValue(tpe: TypeRepr, expr: Tree) {
    // equal trees should have equal hash codes; if trees are equal structurally they should have the same toString?
    override def hashCode() = expr.toString().hashCode

    override def equals(obj: scala.Any) = obj match {
      case EligibleValue(_, e) => expr.asExpr.matches(e.asExpr)
      case _ => false
    }
  }

  class EligibleValues(values: Map[Scope, List[EligibleValue]]) {

    private def doFindInScope(tpe: TypeRepr, scope: Scope): List[Tree] = {
      for( scopedValue <- values.getOrElse(scope, Nil) if checkCandidate(target = tpe, tpt = scopedValue.tpe)) yield {
        scopedValue.expr
      }
    }

    private def uniqueTrees(trees: List[Tree]): Iterable[Tree] = {
      // the only reliable method to compare trees is using structural equality, but there shouldn't be a lot of
      // trees with a given type, so the n^2 complexity shouldn't hurt
      def addIfUnique(addTo: List[Tree], t: Tree): List[Tree] = {
        addTo.find(_.asExpr.matches(t.asExpr)).fold(t :: addTo)(_ => addTo)
      }

      trees.foldLeft(List.empty[Tree])(addIfUnique)
    }

    def findInScope(tpe: TypeRepr, scope: Scope): Iterable[Tree] = {
      uniqueTrees(doFindInScope(tpe, scope))
    }

    def findInFirstScope(tpe: TypeRepr, startingWith: Scope = Scope.Local): Iterable[Tree] = {
      @tailrec
      def forScope(scope: Scope): Iterable[Tree] = {
        findInScope(tpe, scope) match {
          case coll if coll.isEmpty && !scope.isMax => forScope(scope.widen)
          case coll if coll.isEmpty => log(s"Could not find $tpe in any scope"); Nil
          case exprs =>
            log(s"Found [${exprs.mkString(", ")}] of type [$tpe] in scope $scope")
            exprs
        }
      }
      forScope(startingWith)
    }

    def findInAllScope(tpe: TypeRepr): Iterable[Tree] = {
      @tailrec
      def accInScope(scope: Scope, acc: List[Tree]): List[Tree] = {
        val newAcc = doFindInScope(tpe, scope) ++ acc
        if( !scope.isMax ) accInScope(scope.widen, newAcc) else newAcc
      }
      uniqueTrees(accInScope(Scope.Local, Nil))
    }
  }

  object EligibleValues {
    val empty: EligibleValues = new EligibleValues(Map.empty)
  }

}

object EligibleValuesFinder {
  abstract class Scope private(val value: Int) extends Ordered[Scope] {
    /** @return the next Scope until Max */
    def widen: Scope

    def isMax: Boolean = widen == this
    override def compare(that: Scope): Int = this.value.compare(that.value)
    override def equals(other: Any): Boolean = other match {
      case otherScope: Scope => this.value == otherScope.value
      case _ => false
    }
    override def hashCode = value.hashCode
  }

  object Scope extends Ordering[Scope] {

    /** The smallest Scope */
    case object Local extends Scope(1) {
      def widen: Scope = Class
    }
    case object Class extends Scope(2) {
      def widen: Scope = ParentOrModule
    }
    case object ParentOrModule extends Scope(3) {
      def widen: Scope = ModuleInParent
    }
    case object ModuleInParent extends Scope(4) {
      def widen: Scope = ModuleInParent
    }

    /** A special scope for values that are located in a block after the wire call
      * and therefore not reachable. */
    case object LocalForward extends Scope(9) {
      def widen: Scope = LocalForward
    }

    override def compare(a: Scope, b: Scope): Int = a.compare(b)
  }
}
