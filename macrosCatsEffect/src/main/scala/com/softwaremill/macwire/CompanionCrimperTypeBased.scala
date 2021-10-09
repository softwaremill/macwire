package com.softwaremill.macwire

import  com.softwaremill.macwire.internals._

import scala.reflect.macros.blackbox

object CompanionCrimperTypeBased {
  def applyTree[C <: blackbox.Context](c: C, log: Logger)(targetType: c.Type, resolver: c.Type => c.Tree): Option[c.Tree] = {
    import c.universe._

    type Resolver = Type => Tree

    lazy val companionType: Option[Type] = if(targetType.companion == NoType) None else Some(targetType.companion)

    def isCompanionApply(method: Symbol): Boolean =
      method.isMethod &&
      method.isPublic &&
      method.asMethod.returnType <:< targetType &&
      method.asMethod.name.decodedName.toString == "apply"

    lazy val applies: Option[List[Symbol]] = log.withBlock(s"Looking for apply methods of Companion Object in [$companionType]") {
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

    def wireParams(resolver: Resolver)(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.map(_.map(p => resolver(p.typeSignature)))

    def applyArgs(resolver: Resolver): Option[List[List[Tree]]] = applyParamLists.map(x => wireParams(resolver)(x))
    def showApply(c: Symbol): String = c.asMethod.typeSignature.toString

    for {
      pl: List[List[Tree]] <- applyArgs(resolver)
      applyMethod: Tree <- applySelect
    } yield pl.foldLeft(applyMethod)((acc: Tree, args: List[Tree]) => Apply(acc, args))
  }

}
