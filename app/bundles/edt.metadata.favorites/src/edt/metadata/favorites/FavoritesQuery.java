/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;


interface FavoritesQuery
{
    boolean isProjectPinned(String projectName);

    boolean hasPinnedObjects(String projectName);

    boolean isObjectEffectivelyPinned(String projectName, Iterable<String> uuidPath);

    long getRevision();
}
