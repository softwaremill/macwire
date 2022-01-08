package com.softwaremill.macwire.internals

import scala.reflect.macros.blackbox

private[macwire] class CompanionCrimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._

  type DependencyResolverType = DependencyResolver[c.type, Type, Tree]

  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe

  lazy val companionType: Option[Type] = CompanionCrimper.companionType(c)(targetType)

  lazy val applies: Option[List[Symbol]] = CompanionCrimper.applies(c, log)(targetType)

  def applyTree(dependencyResolver: DependencyResolverType): Option[Tree] =
    CompanionCrimper.applyTree[C](c, log)(targetType, dependencyResolver.resolve(_, _))

}

object CompanionCrimper {
  private def showApply[C <: blackbox.Context](c: C)(s: c.Symbol): String = s.asMethod.typeSignature.toString

  private def isCompanionApply[C <: blackbox.Context](c: C)(targetType: c.Type, method: c.Symbol): Boolean =
    method.isMethod &&
      method.isPublic &&
      method.asMethod.returnType <:< targetType &&
      method.asMethod.name.decodedName.toString == "apply"

  private def companionType[C <: blackbox.Context](c: C)(targetType: c.Type): Option[c.Type] = {
    import c.universe._

    if (targetType.companion == NoType) None else Some(targetType.companion)
  }

  private def applies[C <: blackbox.Context](c: C, log: Logger)(targetType: c.Type): Option[List[c.Symbol]] =
    log.withBlock("Looking for apply methods of Companion Object") {
      val as: Option[List[c.Symbol]] =
        companionType(c)(targetType).map(_.members.filter(CompanionCrimper.isCompanionApply(c)(targetType, _)).toList)
      as.foreach(x => log.withBlock(s"There are ${x.size} apply methods:") { x.foreach(s => log(showApply(c)(s))) })
      as
    }

  def applyTree[C <: blackbox.Context](
      c: C,
      log: Logger
  )(targetType: c.Type, resolver: (c.Symbol, c.Type) => c.Tree): Option[c.Tree] = 
    applyFactory(c, log)(targetType).map { case (paramLists, factory) =>
    
    import c.universe._

    val targetTypeD = targetType.dealias

    def wireParams(paramList: List[Symbol]): List[Tree] =
      paramList.map(p => resolver(p, paramType(c)(targetTypeD, p)))

    lazy val applyArgs: List[List[Tree]] = paramLists.map(x => wireParams(x))

    factory(applyArgs)
  }

  def applyFactory[C <: blackbox.Context](
      c: C,
      log: Logger
  )(targetType: c.Type): Option[(List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)] = {
    import c.universe._

    lazy val apply: Option[Symbol] = CompanionCrimper
      .applies(c, log)(targetType)
      .flatMap(_ match {
        case applyMethod :: Nil => Some(applyMethod)
        case _                  => None
      })

    lazy val applySelect: Option[Select] = apply.map(a => Select(Ident(targetType.typeSymbol.companion), a))

    lazy val applyParamLists: Option[List[List[Symbol]]] = apply.map(_.asMethod.paramLists)

    def factory(applyMethod: Tree)(applyArgs: List[List[Tree]]) =
      applyArgs.foldLeft(applyMethod)((acc: Tree, args: List[Tree]) => Apply(acc, args))

      for {
        params <- applyParamLists
        applyMethod <- applySelect
      } yield (params, factory(applyMethod)(_))
  }

}
