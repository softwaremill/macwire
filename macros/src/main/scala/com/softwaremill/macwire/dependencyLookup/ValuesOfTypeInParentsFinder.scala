package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.{PositionUtil, Debug, TypeCheckUtil}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox.Context

private[dependencyLookup] class ValuesOfTypeInParentsFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)
  private val positionUtil = new PositionUtil[c.type](c)

  def find(t: Type, implicitValue: Option[Tree] = None): List[Tree] = {

    def findInParent(parent: Tree): Set[Name] = {
      debug.withBlock(s"Checking parent: [$parent]") {
        val parentType = if (parent.tpe == null) {
          debug("Parent type is null. Creating an expression of parent's type and type-checking that expression ...")

          val tpe = parent match {
            case q"$tpe(..$params)" => tpe
            case q"$tpe" =>  tpe
          }

          /*
          It sometimes happens that the parent type is not yet calculated; this seems to be the case if for example
          the parent is in the same compilation unit, but different package.

          To get the type we need to invoke type-checking on some expression that has the type of the parent. There's
          a lot of expressions to choose from, here we are using the expression "identity[<parent>](null)".

          In order to construct the tree, we borrow some elements from a reified expression for String. To get the
          desired expression we need to swap the String part with parent.
           */
          typeCheckUtil.typeCheckExpressionOfType(tpe)
        } else {
          parent.tpe
        }
        val names: Set[String] = parentType.members.filter { symbol =>
            // filter out values already found by implicitValuesFinder
            implicitValue.map(iv => !positionUtil.samePosition(iv.symbol.pos, symbol.pos)).getOrElse(true) &&
              typeCheckUtil.checkCandidate(target = t, tpt = symbol.typeSignature)
          }.map { symbol =>
            // For (lazy) vals, the names have a space at the end of the name (probably some compiler internals).
            // Hence the trim.
            symbol.name.decodedName.toString.trim()
          }(collection.breakOut)
        if (names.size > 0) {
          debug(s"Found ${names.size} matching name(s): [${names.mkString(", ")}]")
        }
        names.map(TermName(_))
      }
    }

    @tailrec
    def findInParents(parents: List[Tree], acc: Set[Name]): Set[Name] = {
      parents match {
        case Nil => acc
        case parent :: tail => findInParents(tail, findInParent(parent) ++ acc)
      }
    }

    val parents = c.enclosingClass match {
      case ClassDef(_, _, _, Template(pp, self, _)) =>
        val selfTypes = self.tpt match {
          case ident : Ident => List(ident)
          case CompoundTypeTree(Template(selfParents,_,_)) => selfParents
          case _ => Nil
        }
        pp ++ selfTypes

      case ModuleDef(_, _, Template(pp, _, _)) => pp
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    findInParents(parents, Set()).map(Ident(_))(collection.breakOut)
  }
}
