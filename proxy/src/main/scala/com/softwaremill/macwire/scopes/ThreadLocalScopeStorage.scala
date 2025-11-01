package com.softwaremill.macwire.scopes

import scala.collection.mutable

trait ThreadLocalScopeStorage extends Scope {
  private val theStorage = new ThreadLocal[ScopeStorage]()

  def get[T](key: String, createT: => T): T = {
    val storage = theStorage.get()

    if (storage == null) {
      throwNotAssociatedWithStorage()
    }

    storage.get(key) match {
      case Some(obj) => obj.asInstanceOf[T]
      case None      => {
        val obj = createT
        storage.set(key, obj)
        obj
      }
    }
  }

  def associate(storage: ScopeStorage): Unit = {
    if (theStorage.get() != null) {
      throw new IllegalStateException("This thread is already associated with a storage!")
    }

    theStorage.set(storage)
  }

  def associate(storage: collection.mutable.Map[String, Any]): Unit = {
    associate(new MapScopeStorage(storage))
  }

  def associate(storage: java.util.Map[String, Any]): Unit = {
    import scala.collection.JavaConverters._
    associate(storage.asScala)
  }

  def associateWithEmptyStorage() = {
    associate(new mutable.HashMap[String, Any]())
  }

  def disassociate() = {
    if (theStorage.get() == null) {
      throwNotAssociatedWithStorage()
    }

    theStorage.remove()
  }

  def withStorage[T](storage: ScopeStorage)(body: => T): T = {
    withStorage(() => associate(storage))(body)
  }

  def withStorage[T](storage: java.util.Map[String, Any])(body: => T): T = {
    import scala.collection.JavaConverters._
    withStorage(() => associate(storage.asScala))(body)
  }

  def withStorage[T](storage: collection.mutable.Map[String, Any])(body: => T): T = {
    withStorage(() => associate(storage))(body)
  }

  def withEmptyStorage[T](body: => T): T = {
    withStorage(() => associateWithEmptyStorage())(body)
  }

  private def withStorage[T](associateWithStorage: () => Unit)(body: => T): T = {
    associateWithStorage()
    try {
      body
    } finally {
      disassociate()
    }
  }

  private def throwNotAssociatedWithStorage() = {
    throw new IllegalStateException("This thread is not associated with a storage!")
  }
}
