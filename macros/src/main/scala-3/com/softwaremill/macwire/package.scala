package com.softwaremill

package object macwire {
  inline def wire[T]: T = ${ MacwireMacros.wireImpl[T] }

  inline def wireSet[T]: Set[T] = ${ MacwireMacros.wireSet_impl[T] }

  //TESTS: wireWith.success
  inline def wireWith[RES](inline factory: () => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,RES](inline factory: (A) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,RES](inline factory: (A,B) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,RES](inline factory: (A,B,C) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,RES](inline factory: (A,B,C,D) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,RES](inline factory: (A,B,C,D,E) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,RES](inline factory: (A,B,C,D,E,F) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,RES](inline factory: (A,B,C,D,E,F,G) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,RES](inline factory: (A,B,C,D,E,F,G,H) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,RES](inline factory: (A,B,C,D,E,F,G,H,I) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,RES](inline factory: (A,B,C,D,E,F,G,H,I,J) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }
  inline def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,RES](inline factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V) => RES): RES = ${ MacwireMacros.wireWith_impl[RES]('factory) }

  def wiredInModule(in: AnyRef): Wired = ???

  inline def wireRec[T]: T = ${ MacwireMacros.wireRecImpl[T] }
}
