/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;


interface GitChangesQuery
{
    GitChangeState getState(Object element);

    boolean isChanged(Object element);
}
