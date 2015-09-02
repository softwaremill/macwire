package com.softwaremill.macwire.scopes

trait ScopeStorage {
  def get(key: String): Option[Any]
  def set(key: String, value: Any)
}

class MapScopeStorage(map: collection.mutable.Map[String, Any]) extends ScopeStorage {
  def get(key: String) = map.get(key)
  def set(key: String, value: Any) { map(key) = value }
}
