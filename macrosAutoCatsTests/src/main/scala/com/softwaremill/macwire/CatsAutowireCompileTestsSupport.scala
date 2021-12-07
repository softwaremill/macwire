package com.softwaremill.macwire

class CatsAutowireCompileTestsSupport extends CompileTestsSupport {
  override val GlobalImports = """
    import com.softwaremill.macwire.autocats._
    import cats.effect._
    """
}
