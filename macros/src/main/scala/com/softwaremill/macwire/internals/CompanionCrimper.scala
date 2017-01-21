package com.softwaremill.macwire.internals

import scala.reflect.macros.blackbox

private[macwire] class CompanionCrimper [C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._

  lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe

  lazy val companionType: Option[Type] = if(targetType.companion == NoType) None else Some(targetType.companion)

  def isCompanionApply(method: Symbol): Boolean =
    method.isMethod &&
    method.isPublic &&
    method.asMethod.returnType <:< targetType &&
    method.asMethod.name.decodedName.toString == "apply"

  lazy val applies: Option[List[Symbol]] = log.withBlock("Looking for apply methods of Companion Object") {
    val as: Option[List[Symbol]] = companionType.map(_.members.filter(isCompanionApply).toList)
    as.foreach(x => log.withBlock(s"There are ${x.size} apply methods:" ) { x.foreach(c => log(showApply(c))) })
    as
  }

  lazy val apply: Option[Symbol] = applies.flatMap( _ match {
    case applyMethod :: Nil => Some(applyMethod)
    case _ => None
  })

  lazy val applySelect: Option[Select] = apply.map(a => Select(Ident(targetType.typeSymbol.companion), a))

  lazy val applyParamLists: Option[List[List[Symbol]]] = apply.map(_.asMethod.paramLists)

  def wireParams(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.map(_.map(p => dependencyResolver.resolve(p, p.typeSignature).get))

  lazy val applyArgs: Option[List[List[Tree]]] = applyParamLists.map(wireParams)

  lazy val applyTree: Option[Tree] = for {
    pl: List[List[Tree]] <- applyArgs
    applyMethod: Tree <- applySelect
  } yield pl.foldLeft(applyMethod)((acc: Tree, args: List[Tree]) => Apply(acc, args))

  def showApply(c: Symbol): String = c.asMethod.typeSignature.toString
}

