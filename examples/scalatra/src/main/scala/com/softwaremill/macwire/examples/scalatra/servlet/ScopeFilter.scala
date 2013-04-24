package com.softwaremill.macwire.examples.scalatra.servlet

import com.softwaremill.macwire.scopes.{ScopeStorage, ThreadLocalScope}
import javax.servlet._
import javax.servlet.http.HttpServletRequest

class ScopeFilter(requestScope: ThreadLocalScope, sessionScope: ThreadLocalScope) extends Filter {
  def init(filterConfig: FilterConfig) {}

  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    println("IN FILTER!")

    val httpRequest = request.asInstanceOf[HttpServletRequest]

    val sessionScopeStorage = new ScopeStorage {
      def get(key: String) = Option(httpRequest.getSession.getAttribute(key))
      def set(key: String, value: Any) {
        httpRequest.getSession.setAttribute(key, value)
      }
    }

    requestScope.withEmptyStorage {
      sessionScope.withStorage(sessionScopeStorage) {
        chain.doFilter(request, response)
      }
    }
  }

  def destroy() {}
}
