package com.softwaremill.macwire.scopes

import scala.collection.mutable

trait ThreadLocalScopeStorage extends Scope {
  private val theStorage = new ThreadLocal[collection.mutable.Map[String, Any]]()

  def get[T](key: String, createT: => T): T = {
    val storage = theStorage.get()

    if (storage == null) {
      throwNotAssociatedWithStorage()
    }

    storage.get(key) match {
      case Some(obj) => obj.asInstanceOf[T]
      case None => {
        val obj = createT
        storage(key) = obj
        obj
      }
    }
  }

  def associate(storage: collection.mutable.Map[String, Any]) {
    if (theStorage.get() != null) {
      throw new IllegalStateException("This thread is already associated with a storage!")
    }

    theStorage.set(storage)
  }

  def associate(storage: java.util.Map[String, Any]) {
    import scala.collection.JavaConverters._
    associate(storage.asScala)
  }

  def associateWithEmptyStorage() {
    associate(new mutable.HashMap[String, Any]())
  }

  def disassociate() {
    if (theStorage.get() == null) {
      throwNotAssociatedWithStorage()
    }

    theStorage.remove()
  }

  def withStorage[T](storage: collection.mutable.Map[String, Any])(body: => T): T = {
    associate(storage)
    try {
      body
    } finally {
      disassociate()
    }
  }

  def withEmptyStorage[T](body: => T): T = {
    associateWithEmptyStorage()
    try {
      body
    } finally {
      disassociate()
    }
  }

  private def throwNotAssociatedWithStorage() {
    throw new IllegalStateException("This thread is not associated with a storage!")
  }
}
