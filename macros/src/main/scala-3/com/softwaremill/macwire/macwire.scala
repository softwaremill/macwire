package com.softwaremill.macwire

import com.softwaremill.macwire.internals.autowire.autowireImpl
import com.softwaremill.macwire.internals.MacwireMacros

/** Create an instance of type `T`, using the given dependencies.
  *
  * Each dependency might be:
  *   - an instance to use
  *   - a function to create an instance
  *   - a class to instantiate to provide a dependency for the types it implements (provided as: `classOf[SomeType]`)
  *   - a `autowireMembersOf(instance)` call, to use the members of the given instance as dependencies
  *
  * Any missing dependenciess will be created using the publicly available primary constructors or apply methods.
  *
  * @see
  *   [[wire]] for a version which uses the context, within which it is called, to find dependencies.
  */
inline def autowire[T](inline dependencies: Any*): T = ${ autowireImpl[T]('dependencies) }

/** Create an instance of type `T`, using the dependencies that can be found in the context. Any value available in the
  * surrounding trait/class/object, as well as any inherited values, are eligible to be used as dependencies.
  *
  * Type `T` will be instanatiated using the publicly available primary constructor or apply method.
  *
  * @see
  *   [[autowire]] for a context-free version, which uses explicitly provided dependencies.
  */
inline def wire[T]: T = ${ MacwireMacros.wireImpl[T] }

/** Collect all instances of the given type, available in the surrounding context (trait/class/object). */
inline def wireSet[T]: Set[T] = ${ MacwireMacros.wireSet_impl[T] }

/** Collect all instances of the given type, available in the surrounding context (trait/class/object), preserving the
  * order of definition.
  */
inline def wireList[T]: List[T] = ${ MacwireMacros.wireList_impl[T] }

inline def wireWith[RES](inline factory: () => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, RES](inline factory: (A) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, RES](inline factory: (A, B) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, RES](inline factory: (A, B, C) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, RES](inline factory: (A, B, C, D) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, RES](inline factory: (A, B, C, D, E) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, F, RES](inline factory: (A, B, C, D, E, F) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, F, G, RES](inline factory: (A, B, C, D, E, F, G) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, F, G, H, RES](inline factory: (A, B, C, D, E, F, G, H) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, F, G, H, I, RES](inline factory: (A, B, C, D, E, F, G, H, I) => RES): RES = ${
  MacwireMacros.wireWith_impl[RES]('factory)
}
inline def wireWith[A, B, C, D, E, F, G, H, I, J, RES](inline factory: (A, B, C, D, E, F, G, H, I, J) => RES): RES =
  ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
inline def wireWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, RES](
    inline factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => RES
): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }

def wiredInModule(in: AnyRef): Wired = ???

/** Create an instance of type `T`, using the dependencies that can be found in the context. Any value available in the
  * surrounding trait/class/object, as well as any inherited values, are eligible to be used as dependencies.
  *
  * Type `T` will be instanatiated using the publicly available primary constructor or apply method.
  *
  * Any missing dependencies will be created using the publicly available primary constructors or apply methods.
  *
  * @see
  *   [[wire]] for a version which doesn't automatically try to create missing dependencies.
  * @see
  *   [[autowire]] for a context-free version, which uses explicitly provided dependencies.
  */
inline def wireRec[T]: T = ${ MacwireMacros.wireRecImpl[T] }

/** Marker method to be used in [[autowire]], to specify that values defined in the given value should be used for
  * wiring.
  */
def autowireMembersOf[T](t: T): T = ???
