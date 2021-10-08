package com.softwaremill.macwire

import  com.softwaremill.macwire.internals._

import scala.reflect.macros.blackbox

class CompanionCrimperTypeBased [C <: blackbox.Context, TypeC <: C#Type](val c: C, log: Logger, tpe: TypeC) {
  import c.universe._

  type DependencyResolverType = DependencyResolver[c.type, Type, Tree]

  lazy val targetType: Type = tpe.asInstanceOf[Type]

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

  def wireParams(dependencyResolver: DependencyResolverType)(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.map(_.map(p => dependencyResolver.resolve(p, p.typeSignature)))

  def applyArgs(dependencyResolver: DependencyResolverType): Option[List[List[Tree]]] = applyParamLists.map(wireParams(dependencyResolver))

  def applyTree(dependencyResolver: DependencyResolverType): Option[Tree] = for {
    pl: List[List[Tree]] <- applyArgs(dependencyResolver)
    applyMethod: Tree <- applySelect
  } yield pl.foldLeft(applyMethod)((acc: Tree, args: List[Tree]) => Apply(acc, args))

  def showApply(c: Symbol): String = c.asMethod.typeSignature.toString
}

