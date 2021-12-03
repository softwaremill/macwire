package com.softwaremill.macwire

package object internals {
  //FIXME any built-in solution?
  def sequence[A](l: List[Option[A]]) = (Option(List.empty[A]) /: l) {
    case (Some(sofar), Some(value)) => Some(value :: sofar);
    case (_, _)                     => None
  }

  def composeOpts[A, B](f: A => Option[B], fs: A => Option[B]*): A => Option[B] = fs.fold(f) { case (f1, f2) =>
    (a: A) => f1(a).orElse(f2(a))
  }
  def composeWithFallback[A, B](f: A => Option[B], fs: A => Option[B]*)(value: A => B): A => B = (a: A) =>
    composeOpts(f, fs: _*)(a).getOrElse(value(a))

  def combine[A, B](fs: Seq[A => B])(op: B => B => B): A => B = fs.reduce { (f1, f2) => (a: A) => op(f1(a))(f2(a)) }
}
