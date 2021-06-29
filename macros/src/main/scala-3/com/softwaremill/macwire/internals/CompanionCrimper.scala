package com.softwaremill.macwire.internals

import scala.quoted.*

private[macwire] class CompanionCrimper [Q <: Quotes, T: Type](using val q: Q)(dependencyResolver: => DependencyResolver[q.type, T], log: Logger) {
  import q.reflect.*
  
  lazy val targetType = TypeRepr.of[T]

  lazy val companionType: Option[Symbol] = targetType.typeSymbol.companionClass match {
    case c if c == Symbol.noSymbol => None
    case c => Some(c)
  }

  def returnType(symbol: Symbol): TypeRepr = symbol.tree match {
    case dd: DefDef => dd.returnTpt.tpe
  }

  def isCompanionApply(method: Symbol): Boolean = 
    method.isDefDef &&
    !(method.flags is Flags.Private) &&
    !(method.flags is Flags.Protected) &&
    returnType(method) <:< targetType &&
    method.name == "apply"

  lazy val applies: Option[List[Symbol]] = log.withBlock("Looking for apply methods of Companion Object") {
    val as: Option[List[Symbol]] = companionType.map(_.declarations.filter(isCompanionApply).toList)

    as.foreach(x => log.withBlock(s"There are ${x.size} apply methods:" ) { x.foreach(c => log(showApply(c))) })
    as
  }

  lazy val apply: Option[Symbol] = applies.flatMap( _ match {
    case applyMethod :: Nil => Some(applyMethod)
    case _ => None
  })

  lazy val applySelect: Option[Select] = apply.map(a => Select(Ref(targetType.typeSymbol.companionModule), a))

  lazy val applyParamLists: Option[List[List[Symbol]]] = apply.map(_.paramSymss)

  def wireParams(paramLists: List[List[Symbol]]): List[List[Term]] = paramLists.map(_.map(p => dependencyResolver.resolve(p, paramType(p))))

  lazy val applyArgs: Option[List[List[Term]]] = applyParamLists.map(wireParams)

  lazy val applyTree: Option[Tree] = for {
    pl: List[List[Term]] <- applyArgs
    applyMethod: Term <- applySelect
  } yield pl.foldLeft(applyMethod)((acc: Term, args: List[Term]) => Apply(acc, args))

    private def paramType(param: Symbol): TypeRepr = {
//FIXME
    Ref(param).tpe.widen
  }

  def showApply(c: Symbol): String = c.toString
}

