package com.softwaremill


package object macwire {
  inline def wire[T]: T = ???

  def wireSet[T]: Set[T] = ???

  def wireWith[RES](factory: () => RES): RES = ???
  def wireWith[A,RES](factory: (A) => RES): RES = ???
  def wireWith[A,B,RES](factory: (A,B) => RES): RES = ???
  def wireWith[A,B,C,RES](factory: (A,B,C) => RES): RES = ???
  def wireWith[A,B,C,D,RES](factory: (A,B,C,D) => RES): RES = ???
  def wireWith[A,B,C,D,E,RES](factory: (A,B,C,D,E) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,RES](factory: (A,B,C,D,E,F) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,RES](factory: (A,B,C,D,E,F,G) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,RES](factory: (A,B,C,D,E,F,G,H) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,RES](factory: (A,B,C,D,E,F,G,H,I) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,RES](factory: (A,B,C,D,E,F,G,H,I,J) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,RES](factory: (A,B,C,D,E,F,G,H,I,J,K) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U) => RES): RES = ???
  def wireWith[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,RES](factory: (A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V) => RES): RES = ???

  def wiredInModule(in: AnyRef): Wired = ???
}
