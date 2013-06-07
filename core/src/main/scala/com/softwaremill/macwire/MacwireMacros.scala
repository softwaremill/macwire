package com.softwaremill.macwire

import language.experimental.macros

import reflect.macros.Context

trait Macwire {
  def wire[T]: T = macro MacwireMacros.wire_impl[T]
}

object MacwireMacros extends Macwire {
  private val debug = new Debug()
  import Util._

  def wire_impl[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._

    def findValueOfType(n: Name, t: Type): Option[Name] = {
      debug.withBlock(s"Trying to find value [$n] of type: [$t]") {
        val namesOpt = firstNotEmpty[Name](
          () => new ValuesOfTypeInEnclosingMethodFinder[c.type](c, debug).find(n, t),
          () => new ValuesOfTypeInEnclosingClassFinder[c.type](c, debug).find(t),
          () => new ValuesOfTypeInParentsFinder[c.type](c, debug).find(t)
        )

        namesOpt match {
          case None => {
            c.error(c.enclosingPosition, s"Cannot find a value of type: [$t]")
            None
          }
          case Some(List(name)) => {
            debug(s"Found single value: [$name] of type [$t]")
            Some(name)
          }
          case Some(names) => {
            c.error(c.enclosingPosition, s"Found multiple values of type [$t]: [$names]")
            None
          }
        }
      }
    }

    def createNewTargetWithParams(): Expr[T] = {
      val targetType = implicitly[c.WeakTypeTag[T]]
      debug.withBlock(s"Trying to find parameters to create new instance of: [${targetType.tpe}]") {
        val targetConstructorOpt = targetType.tpe.members.find(_.name.decoded == "<init>")
        targetConstructorOpt match {
          case None => {
            c.error(c.enclosingPosition, "Cannot find constructor for " + targetType)
            reify { null.asInstanceOf[T] }
          }
          case Some(targetConstructor) => {
            val targetConstructorParams = targetConstructor.asMethod.paramss.flatten

            val newT = Select(New(Ident(targetType.tpe.typeSymbol)), nme.CONSTRUCTOR)

            val constructorParams = for (param <- targetConstructorParams) yield {
              val wireToOpt = findValueOfType(param.name, param.typeSignature).map(Ident(_))

              // If no value is found, an error has been already reported.
              wireToOpt.getOrElse(reify(null).tree)
            }

            val newTWithParams = Apply(newT, constructorParams)
            debug(s"Generated code: ${c.universe.show(newTWithParams)}")
            c.Expr(newTWithParams)
          }
        }
      }
    }

    createNewTargetWithParams()
  }
}
