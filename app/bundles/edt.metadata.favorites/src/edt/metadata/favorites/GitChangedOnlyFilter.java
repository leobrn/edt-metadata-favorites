/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;


public class GitChangedOnlyFilter
    extends ViewerFilter
{
    public static final String ID = "edt.metadata.favorites.gitChangedFilter";

    private final Map<Object, Boolean> descendantCache = new IdentityHashMap<>();

    private final GitChangesQuery changes;

    private boolean descendantCacheClearScheduled;

    public GitChangedOnlyFilter()
    {
        this(Activator.getDefault().getGitChangeCache());
    }

    GitChangedOnlyFilter(GitChangesQuery changes)
    {
        this.changes = Objects.requireNonNull(changes);
    }

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element)
    {
        GitChangeState state = changes.getState(element);
        if (state == GitChangeState.UNKNOWN)
        {
            return true;
        }
        if (state == GitChangeState.CLEAN)
        {
            return false;
        }
        if (state == GitChangeState.DIRTY && changes.isChanged(element)
            && isPinnedWhenFavoriteFilterIsActive(viewer, element))
        {
            return true;
        }
        if (viewer instanceof AbstractTreeViewer treeViewer)
        {
            return hasChangedDescendant(treeViewer, element);
        }
        return false;
    }

    private boolean hasChangedDescendant(AbstractTreeViewer viewer, Object element)
    {
        Boolean cached = descendantCache.get(element);
        if (cached != null)
        {
            return cached;
        }

        boolean cacheable = ensureDescendantCacheClearScheduled(viewer);
        boolean result = computeHasChangedDescendant(viewer, element);
        if (cacheable)
        {
            descendantCache.put(element, result);
        }
        return result;
    }

    private boolean ensureDescendantCacheClearScheduled(Viewer viewer)
    {
        if (descendantCacheClearScheduled)
        {
            return true;
        }
        Display display = displayOf(viewer);
        if (display == null)
        {
            return false;
        }
        descendantCacheClearScheduled = true;
        display.asyncExec(() -> {
            descendantCache.clear();
            descendantCacheClearScheduled = false;
        });
        return true;
    }

    private static Display displayOf(Viewer viewer)
    {
        Control control = viewer.getControl();
        if (control != null && !control.isDisposed())
        {
            return control.getDisplay();
        }
        return Display.getCurrent();
    }

    private boolean computeHasChangedDescendant(AbstractTreeViewer viewer, Object element)
    {
        if (!(viewer.getContentProvider() instanceof ITreeContentProvider contentProvider))
        {
            return false;
        }

        Object[] children = contentProvider.getChildren(element);
        if (children == null)
        {
            return false;
        }

        for (Object child : children)
        {
            GitChangeState state = changes.getState(child);
            if (state == GitChangeState.UNKNOWN)
            {
                return true;
            }
            if (state == GitChangeState.CLEAN)
            {
                continue;
            }
            if (state == GitChangeState.DIRTY && changes.isChanged(child)
                && isPinnedWhenFavoriteFilterIsActive(viewer, child))
            {
                return true;
            }
            if (hasChangedDescendant(viewer, child))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isPinnedWhenFavoriteFilterIsActive(Viewer viewer, Object element)
    {
        if (!(viewer instanceof AbstractTreeViewer treeViewer))
        {
            return true;
        }
        return isPinnedWhenFavoriteFilterIsActive(treeViewer.getFilters(), element);
    }

    static boolean isPinnedWhenFavoriteFilterIsActive(ViewerFilter[] filters, Object element)
    {
        for (ViewerFilter filter : filters)
        {
            if (filter instanceof PinnedOnlyFilter pinnedFilter)
            {
                return pinnedFilter.isPinnedElement(element);
            }
        }
        return true;
    }
}
