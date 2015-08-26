package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.{PositionUtil, Debug, TypeCheckUtil}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox.Context

private[dependencyLookup] class ValuesOfTypeInEnclosingClassFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)
  private val positionUtil = new PositionUtil[c.type](c)

  object ValDefOrDefDef {
    def unapply(t: Tree): Option[(TermName, Tree, Tree, Symbol)] = t match {
      case ValDef(_, name, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case DefDef(_, name, _, _, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case _ => None
    }
  }

  /**
   * @param implicitValue If a matching value will be found that has the same position as a matching implicit
   *                      value, the implicit value's tree will be used instead of a plain `Ident`, so that
   *                      it can be filter out later. (We still need to include it in the results, so that
   *                      we know that the search *did* find something and doesn't proceed to parents.)
   */
  def find(t: Type, implicitValue: Option[Tree]): List[Tree] = {

    @tailrec
    def doFind(trees: List[Tree], acc: List[Tree]): List[Tree] = {
      trees match {
        case Nil => acc
        case tree :: tail => tree match {
          case ValDefOrDefDef(name, tpt, rhs, symbol) =>
            val candidateOk = typeCheckUtil.checkCandidate(t, name, tpt, treeToCheck(tree, rhs),
              if (symbol.isMethod) "def" else "val")

            if (candidateOk) {
              val treeToAdd = implicitValue match {
                case Some(i) if positionUtil.samePosition(i.symbol.pos, symbol.pos) => i
                case _ => Ident(name)
              }

              doFind(tail, treeToAdd :: acc)
            } else doFind(tail, acc)

          case Import(expr, selectors) =>
            val matches = debug.withBlock(s"Looking up imports in [$tree]") {

              val importCandidates =
                if (selectors.exists { selector => selector.name.toString == "_" }) {
                  // wildcard import on `expr`
                  typeCheckUtil.typeCheckIfNeeded(expr).members.filter { _.isPublic }.toList
                } else {
                  val selectorNames = selectors.map(_.name).toSet
                  typeCheckUtil.typeCheckIfNeeded(expr).members.filter { m => selectorNames.contains(m.name) }.toList
                }

              filterImportMembers(importCandidates)
            }
            doFind(tail, matches ::: acc)

          case _ => doFind(tail, acc)
        }
      }
    }

    def filterImportMembers(members: List[Symbol]) : List[Tree] = {
      members.filter { m =>
        debug.withBlock(s"Checking [$m]") {
          if( m.isImplicit ) {
            // ignore implicits as they will be picked by `ImplicitValueOfTypeFinder`
            debug("Ignoring implicit (will be picked later on)")
            false
          } else {
            val ok = typeCheckUtil.checkCandidate(target = t, tpt = m.typeSignature)
            if (ok) debug("Found a match!")
            ok
          }
        }
      }.map { m => Ident(m.name) }
    }

    def treeToCheck(tree: Tree, rhs: Tree) = {
      // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
      // type ascription needed errors from the compiler.
      if (rhs.isEmpty) tree else rhs
    }

    val enclosingClassBody = c.enclosingClass match {
      case ClassDef(_, _, _, Template(_, _, body)) => body
      case ModuleDef(_, _, Template(_, _, body)) => body
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    debug("Looking in the enclosing class/trait")
    doFind(enclosingClassBody, Nil)
  }
}
