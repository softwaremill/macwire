package com.softwaremill.macwire

import com.softwaremill.macwire.dependencyLookup._

import scala.reflect.macros.blackbox

object MacwireMacros {
  private val log = new Logger()

  def wire_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] = {
    import c.universe._

    def abort(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

    def tryCompanionObject(targetType: Type): Tree = {
      if (targetType.companion == NoType) {
        abort(s"Cannot find a public constructor nor a companion object for $targetType")
      } else {

        val isCompanionMethodFactory = (method: Symbol) => {
          method.isMethod &&
          method.isPublic &&
          method.asMethod.returnType <:< targetType &&
          method.asMethod.name.decodedName.toString == "apply"
        }

        targetType.companion.members.filter(isCompanionMethodFactory).toList match {
          case Nil => abort(s"Cannot find a public constructor for $targetType, nor apply method in its companion object")
          case applyMethod :: Nil =>
            wireParameters(
              Select(Ident(targetType.typeSymbol.companion), applyMethod),
              applyMethod.asMethod.paramLists,
              _.typeSignature)

          case moreThanOne => abort(s"No public primary constructor found for $targetType and " +
            "multiple matching apply method in its companion object were found.")
        }
      }
    }

    def wireParameters(constructionMethodTree: Tree, paramLists: List[List[Symbol]], resolveType: c.Symbol => c.Type): Tree = {
      filterOutImplicitParams(paramLists).foldLeft(constructionMethodTree) { case (applicationTree, paramList) =>
        val constructorParams: List[Tree] = for (param <- paramList) yield {
          val wireToOpt = dependencyResolver.resolve(param, resolveType(param))

          // If no value is found, an error has been already reported.
          wireToOpt.getOrElse(reify(null).tree)
        }
        Apply(applicationTree, constructorParams)
      }
    }

    def filterOutImplicitParams(targetConstructorParamLists: List[List[Symbol]]): List[List[Symbol]] = {
      targetConstructorParamLists.filterNot(_.headOption.exists(_.isImplicit))
    }

    def wireConstructor(targetType: Type, targetConstructor: Symbol): Tree = {
      // We need to get the "real" type in case the type parameter is a type alias - then it cannot
      // be directly instantiated
      val targetTpe = targetType.dealias

      val (sym, tpeArgs) = targetTpe match {
        case TypeRef(_, sym, tpeArgs) => (sym, tpeArgs)
        case t => abort(s"Target type not supported for wiring: $t. Please file a bug report with your use-case.")
      }

      def paramType(param: Symbol): Type = {
        val pTpe = param.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)

        if (param.asTerm.isByNameParam) {
          pTpe.typeArgs.head
        } else {
          pTpe
        }
      }

      val constructionMethodTree = Select(New(Ident(targetTpe.typeSymbol)), termNames.CONSTRUCTOR)

      wireParameters(
        constructionMethodTree,
        targetConstructor.asMethod.paramLists,
        sym => paramType(sym)) // SI-4751
    }

    def createNewTargetWithParams(): Expr[T] = {
      val isInjectAnnotation = (a: Annotation) => a.toString == "javax.inject.Inject"

      val targetType = implicitly[c.WeakTypeTag[T]].tpe
      log.withBlock(s"Trying to find parameters to create new instance of: [$targetType] at ${c.enclosingPosition}") {
        val publicCtors = targetType.members.filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
        val injectCtor = publicCtors.find(_.annotations.exists(isInjectAnnotation))
        val targetConstructorOpt = injectCtor.orElse(publicCtors.find(_.asMethod.isPrimaryConstructor))

        val code = targetConstructorOpt match {
          case None =>
            tryCompanionObject(targetType)

          case Some(targetConstructor) =>
            wireConstructor(targetType, targetConstructor)
        }
        log(s"Generated code: ${showRaw(code)}")
        c.Expr(code)
      }
    }

    createNewTargetWithParams()
  }

  def wireWith_impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Tree = {
    import c.universe._

    val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val dependencyResolver = new DependencyResolver[c.type](c, log)
    import typeCheckUtil.typeCheckIfNeeded

    val Block(Nil, Function(params, Apply(fun, _))) = factory
    val values = params.map {
      case vd @ ValDef(_, name, tpt, rhs) =>
        dependencyResolver.resolve(vd.symbol, typeCheckIfNeeded(tpt)).getOrElse(reify(null).tree)
    }
    val code = q"$fun(..$values)"

    log("Generated code: " + showCode(code))
    code
  }

  def wireSet_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    val targetType = implicitly[c.WeakTypeTag[T]]

    val dependencyResolver = new DependencyResolver[c.type](c, log)

    val instances = dependencyResolver.resolveAll(targetType.tpe)

    // The lack of hygiene can be seen here as a feature, the choice of Set implementation
    // is left to the user - you want a `mutable.Set`, just import `mutable.Set` before the `wireSet[T]` call
    val code = q"Set(..$instances)"

    log("Generated code: " + show(code))
    code
  }

  def wiredInModule_impl(c: blackbox.Context)(in: c.Expr[AnyRef]): c.Tree = {
    import c.universe._

    def extractTypeFromNullaryType(tpe: Type) = {
      tpe match {
        case NullaryMethodType(underlying) => Some(underlying)
        case _ => None
      }
    }

    val capturedIn = TermName(c.freshName())

    def instanceFactoriesByClassInTree(tree: Tree): List[Tree] = {
      val members = tree.tpe.members

      val pairs = members
        .filter(s => s.isMethod && s.isPublic)
        .flatMap { m =>
          extractTypeFromNullaryType(m.typeSignature) match {
            case Some(tpe) => Some((m, tpe))
            case None =>
              log(s"Cannot extract type from ${m.typeSignature} for member $m!")
              None
          }
        }
        .filter { case (_, tpe) => tpe <:< typeOf[AnyRef] }
        .map { case (member, tpe) =>
          val key = Literal(Constant(tpe))
          val value = q"$capturedIn.$member"

          log(s"Found a mapping: $key -> $value")

          q"scala.Predef.ArrowAssoc($key) -> (() => $value)"
        }

      pairs.toList
    }

    log.withBlock(s"Generating wired-in-module for ${in.tree}") {
      val pairs = instanceFactoriesByClassInTree(in.tree)

      val code = q"""
          val $capturedIn = $in
          com.softwaremill.macwire.Wired(scala.collection.immutable.Map(..$pairs))
       """

      log(s"Generated code: " + show(code))
      code
    }
  }
}
