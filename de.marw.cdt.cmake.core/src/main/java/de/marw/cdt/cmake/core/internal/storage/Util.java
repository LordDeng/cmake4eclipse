/*******************************************************************************
 * Copyright (c) 2014 Martin Weber.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package de.marw.cdt.cmake.core.internal.storage;

import java.util.Collection;

import org.eclipse.cdt.core.settings.model.ICStorageElement;

/**
 * @author Martin Weber
 */
public class Util {

  /**
   * Nothing to instantiate here, just static methods.
   */
  private Util() {
  }

  /**
   * Converts a collection of {@code T} objects to {@link ICStorageElement}s.
   * @param targetCollectionStorageName
   *        the name for the ICStorageElement representing the collection. It
   *        will be created.
   * @param parent
   *        the parent element, must not be {@code null}.
   * @param itemSerializer
   *        the object that converts a collection element.
   * @param source
   *        the collection to convert, must not be {@code null}.
   */
  public static <E> void serializeCollection(String targetCollectionStorageName,
      ICStorageElement parent, StorageSerializer<E> itemSerializer,
      Collection<E> source) {
    if (!source.isEmpty()) {
      ICStorageElement pColl = parent.createChild(targetCollectionStorageName);
      for (E elem : source) {
        itemSerializer.toStorage(pColl, elem);
      }
    }
  }

  /**
   * Converts an {@link ICStorageElement} to a collection of {@code T} objects.
   * @param target
   *        the collection to store the converted objects in, must not be
   *        {@code null}.
   * @param itemSerializer
   *        the object that converts a collection element to an Object.
   * @param sourceParent
   *        the parent element of the collection to read, must not be
   *        {@code null}.
   */
  public static <E> void deserializeCollection(Collection<E> target,
      StorageSerializer<E> itemSerializer, ICStorageElement sourceParent) {
    for (ICStorageElement elem : sourceParent
        .getChildren()) {
      E item = itemSerializer.fromStorage(elem);
      if (item != null)
        target.add(item);
    }
  }

}
