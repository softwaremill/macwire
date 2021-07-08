package com.softwaremill

import scala.language.experimental.macros

package object macwire {
  def wire[T]: T = macro MacwireMacros.wire_impl[T]

  def wireSet[T]: Set[T] = macro MacwireMacros.wireSet_impl[T]

  def wireWith[RES](factory: () => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,RES](factory: (A) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,RES](factory: (A,B) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,RES](factory: (A,B,C) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,RES](factory: (A,B,C,D) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,RES](factory: (A,B,C,D,E) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,RES](factory: (A,B,C,D,E,F) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,RES](factory: (A,B,C,D,E,F,G) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,RES](factory: (A,B,C,D,E,F,G,H) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,RES](factory: (A,B,C,D,E,F,G,H,I) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,RES](factory: (A,B,C,D,E,F,G,H,I,J) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,RES](factory: (A,B,C,D,E,F,G,H,I,J,K) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U) => RES): RES = macro MacwireMacros.wireWith_impl[RES]
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V) => RES): RES = macro MacwireMacros.wireWith_impl[RES]

  def wiredInModule(in: AnyRef): Wired = macro MacwireMacros.wiredInModule_impl

  def wireRec[T]: T = macro MacwireMacros.wireRec_impl[T]

}
