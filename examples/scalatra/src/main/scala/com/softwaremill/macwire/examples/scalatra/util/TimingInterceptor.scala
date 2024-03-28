package com.softwaremill.macwire.examples.scalatra.util

import com.softwaremill.macwire.aop.{InvocationContext, ProxyingInterceptor}

object TimingInterceptor extends ProxyingInterceptor {
  def handle(ctx: InvocationContext) = {
    val classWithMethodName = s"${ctx.target.getClass.getSimpleName}.${ctx.method.getName}"
    val start = System.currentTimeMillis()
    println(s"Invoking $classWithMethodName...")
    try {
      ctx.proceed()
    } finally {
      val end = System.currentTimeMillis()
      println(s"Invocation of $classWithMethodName took: ${end - start}ms")
    }
  }
}
