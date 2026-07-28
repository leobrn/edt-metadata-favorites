/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.eclipse.core.resources.IProject;
import org.junit.Test;


public class PinnedOnlyFilterTest
{
    @Test
    public void pinnedProjectStaysVisible()
    {
        TestFavoritesQuery query = new TestFavoritesQuery();
        query.projectPinned = true;

        assertTrue(new PinnedOnlyFilter(query).select(null, null, project("SM")));
    }

    @Test
    public void unpinnedProjectWithoutPinnedObjectsIsHidden()
    {
        TestFavoritesQuery query = new TestFavoritesQuery();

        assertFalse(new PinnedOnlyFilter(query).select(null, null, project("SM")));
        assertEquals(1, query.hasPinnedObjectsCalls);
    }

    @Test
    public void revisionChangeInvalidatesHasPinnedObjectsCache()
    {
        TestFavoritesQuery query = new TestFavoritesQuery();
        PinnedOnlyFilter filter = new PinnedOnlyFilter(query);
        IProject project = project("SM");

        assertFalse(filter.select(null, null, project));
        assertEquals(1, query.hasPinnedObjectsCalls);

        query.hasPinnedObjects = true;
        assertFalse(filter.select(null, null, project));
        assertEquals(1, query.hasPinnedObjectsCalls);

        query.revision++;
        filter.select(null, null, project);
        assertEquals(2, query.hasPinnedObjectsCalls);
    }

    private static IProject project(String name)
    {
        return (IProject)Proxy.newProxyInstance(PinnedOnlyFilterTest.class.getClassLoader(),
            new Class<?>[] {IProject.class}, (proxy, method, arguments) -> {
                if ("getName".equals(method.getName()))
                {
                    return name;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static final class TestFavoritesQuery
        implements FavoritesQuery
    {
        private boolean projectPinned;

        private boolean hasPinnedObjects;

        private int hasPinnedObjectsCalls;

        private long revision;

        @Override
        public boolean isProjectPinned(String projectName)
        {
            return projectPinned;
        }

        @Override
        public boolean hasPinnedObjects(String projectName)
        {
            hasPinnedObjectsCalls++;
            return hasPinnedObjects;
        }

        @Override
        public boolean isObjectEffectivelyPinned(String projectName, Iterable<String> uuidPath)
        {
            throw new AssertionError("Object query is not expected for a project fast-path");
        }

        @Override
        public long getRevision()
        {
            return revision;
        }
    }
}
