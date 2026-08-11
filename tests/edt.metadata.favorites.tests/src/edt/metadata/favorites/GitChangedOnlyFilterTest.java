/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ViewerFilter;
import org.junit.Test;


public class GitChangedOnlyFilterTest
{
    @Test
    public void changedElementStaysVisible()
    {
        Object element = new Object();
        TestGitChangesQuery changes = new TestGitChangesQuery();
        changes.state = GitChangeState.DIRTY;
        changes.changedElement = element;

        assertTrue(new GitChangedOnlyFilter(changes).select(null, null, element));
    }

    @Test
    public void cleanElementIsHidden()
    {
        TestGitChangesQuery changes = new TestGitChangesQuery();
        changes.state = GitChangeState.CLEAN;

        assertFalse(new GitChangedOnlyFilter(changes).select(null, null, new Object()));
    }

    @Test
    public void treeStaysVisibleWhileStatusIsLoading()
    {
        TestGitChangesQuery changes = new TestGitChangesQuery();
        changes.state = GitChangeState.UNKNOWN;

        assertTrue(new GitChangedOnlyFilter(changes).select(null, null, new Object()));
    }

    @Test
    public void activeFavoriteFilterRejectsChangedElementOutsideFavorites()
    {
        PinnedOnlyFilter favorites = new PinnedOnlyFilter(new TestFavoritesQuery());

        assertFalse(GitChangedOnlyFilter.isPinnedWhenFavoriteFilterIsActive(
            new ViewerFilter[] {favorites}, project("SM")));
    }

    private static IProject project(String name)
    {
        return (IProject)Proxy.newProxyInstance(GitChangedOnlyFilterTest.class.getClassLoader(),
            new Class<?>[] {IProject.class}, (proxy, method, arguments) -> {
                if ("getName".equals(method.getName()))
                {
                    return name;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static final class TestGitChangesQuery
        implements GitChangesQuery
    {
        private GitChangeState state;

        private Object changedElement;

        @Override
        public GitChangeState getState(Object element)
        {
            return state;
        }

        @Override
        public boolean isChanged(Object element)
        {
            return element == changedElement;
        }
    }

    private static final class TestFavoritesQuery
        implements FavoritesQuery
    {
        @Override
        public boolean isProjectPinned(String projectName)
        {
            return false;
        }

        @Override
        public boolean hasPinnedObjects(String projectName)
        {
            return false;
        }

        @Override
        public boolean isObjectEffectivelyPinned(String projectName, Iterable<String> uuidPath)
        {
            return false;
        }

        @Override
        public long getRevision()
        {
            return 0;
        }
    }
}
