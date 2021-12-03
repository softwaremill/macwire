package com.softwaremill.macwire

import com.softwaremill.macwire.internals._

import scala.reflect.macros.blackbox

object AutowireMacros {
  private val log = new Logger()

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[T] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]

    val providersEntities = new Providers[c.type](c, log)
    import providersEntities._

    def providerFromExpr(expr: Expr[Any]): Provider = composeWithFallback[Tree, Provider](FactoryMethod.fromTree)(new Instance(_))(expr.tree)

    log.withBlock(s"Autowire instance of [$targetType] with dependencies [${dependencies.mkString(", ")}]") {
      val providers = dependencies.map(providerFromExpr)

      def findProvider(tpe: Type): Option[Tree] = providers.find(_.`type` <:< tpe).map {
        case i: Instance       => i.ident
        case fm: FactoryMethod => fm.maybeResult(findProvider(_)).getOrElse(c.abort(c.enclosingPosition, "TODO3")).ident
      }

      val code = wireWithResolver(c)(findProvider(_)) getOrElse c.abort(c.enclosingPosition, s"Failed for [$targetType]") //FIXME Improve error tracing
      log(s"Code: [$code]")

      c.Expr[T](code)
    }
  }

  private def wireWithResolver[T: c.WeakTypeTag](
      c: blackbox.Context
  )(resolver: c.Type => Option[c.Tree]) = {
    import c.universe._

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
      if (isWireable(tpe)) resolver(tpe).orElse(go(tpe)).getOrElse(c.abort(c.enclosingPosition, s"TODO???"))
      else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

    def go(t: Type): Option[Tree] =
      (ConstructorCrimper.constructorTree(c, log)(t, resolutionWithFallback) orElse CompanionCrimper
        .applyTree(c, log)(t, resolutionWithFallback))

    go(implicitly[c.WeakTypeTag[T]].tpe)
  }
}
